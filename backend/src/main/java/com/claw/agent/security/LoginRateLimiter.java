package com.claw.agent.security;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流器（内存型）：按「用户名 + IP」维度计数，防爆破。
 * <p>
 * 规则：{@link #WINDOW_MINUTES} 分钟内连续失败 {@link #MAX_FAILURES} 次即锁定
 * {@link #LOCK_MINUTES} 分钟，锁定期间拒绝尝试（不区分密码对错，避免被探测）；
 * 登录成功立即清零。惰性清理：访问时判断过期即移除，无需后台线程。
 * <p>
 * 限制：内存态重启清零且仅单实例有效，多实例部署需切换为 Redis 计数（键与语义一致）。
 */
@Component
public class LoginRateLimiter {

    /** 失败计数窗口（分钟） */
    private static final int WINDOW_MINUTES = 5;

    /** 窗口内允许的最大失败次数 */
    private static final int MAX_FAILURES = 5;

    /** 触发后的锁定时长（分钟） */
    private static final int LOCK_MINUTES = 5;

    /** 键（用户名|IP）→ 状态 [0]=失败次数 [1]=窗口起点毫秒 [2]=锁定截止毫秒 */
    private final Map<String, long[]> states = new ConcurrentHashMap<>();

    /**
     * 登录前检查是否处于锁定状态。
     *
     * @param username 用户名
     * @param ip       客户端 IP（可为空，仅按用户名计数）
     * @throws BizException 锁定期间直接拒绝（提示剩余时间）
     */
    public void check(String username, String ip) {
        long[] state = states.get(key(username, ip));
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (state[2] > now) {
            long minutes = (state[2] - now) / 60_000 + 1;
            throw new BizException(ResultCode.PARAM_ERROR, "登录失败次数过多，请 " + minutes + " 分钟后再试");
        }
        // 锁定已过期且窗口已过期：顺手清理
        if (state[1] + WINDOW_MINUTES * 60_000L < now) {
            states.remove(key(username, ip));
        }
    }

    /**
     * 记录一次登录失败：窗口内累计，达阈值转入锁定。
     *
     * @param username 用户名
     * @param ip       客户端 IP（可为空）
     */
    public void recordFailure(String username, String ip) {
        String k = key(username, ip);
        long now = System.currentTimeMillis();
        states.compute(k, (key, old) -> {
            // 无记录或窗口已过期：开启新窗口
            if (old == null || old[1] + WINDOW_MINUTES * 60_000L < now) {
                return new long[]{1, now, 0};
            }
            long[] next = new long[]{old[0] + 1, old[1], old[2]};
            if (next[0] >= MAX_FAILURES) {
                next[2] = now + LOCK_MINUTES * 60_000L;
            }
            return next;
        });
    }

    /** 登录成功后清除该维度的失败计数 */
    public void clear(String username, String ip) {
        states.remove(key(username, ip));
    }

    /** 计数维度键：用户名小写 + IP，避免大小写绕过与代理后多 IP 独立计数 */
    private String key(String username, String ip) {
        return username.toLowerCase() + "|" + (ip == null ? "" : ip);
    }
}
