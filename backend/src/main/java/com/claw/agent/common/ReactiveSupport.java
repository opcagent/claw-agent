package com.claw.agent.common;

import com.claw.agent.config.security.ClientIpFilter;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.mapper.OperLogMapper;
import com.claw.agent.model.OperLog;
import com.claw.agent.security.LoginUser;
import com.claw.agent.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 响应式控制器统一支撑工具：消除每个接口的
 * {@code SecurityUtil.currentUser().flatMap(u -> Mono.fromCallable(...).subscribeOn(...))} 样板。
 * <p>
 * 统一完成四件事：
 * <ol>
 *   <li>从响应式安全上下文提取当前登录用户（未认证抛 401）；</li>
 *   <li>业务逻辑切换到 boundedElastic 线程池执行（避免阻塞事件循环）；</li>
 *   <li>执行期间把用户写入 {@link UserContextHolder}（供 MyBatis Plus 审计填充取创建人/修改人），结束后清理；</li>
 *   <li>把链路 traceId 写入 MDC（业务线程日志可按 traceId 聚合），结束后清理；</li>
 *   <li>带日志重载时，成功/失败均落 {@code sys_oper_log}。</li>
 * </ol>
 */
@Slf4j
public final class ReactiveSupport {

    private ReactiveSupport() {
    }

    /**
     * 执行有返回值的业务逻辑并包装为 {@code Result}（不记操作日志，用于查询类接口）。
     *
     * @param fn 业务逻辑，入参为当前登录用户
     * @param <T> 返回数据类型
     * @return Result 包装的 Mono
     */
    public static <T> Mono<Result<T>> call(Function<LoginUser, T> fn) {
        return execute(fn, null);
    }

    /**
     * 执行无返回值的业务逻辑（不记操作日志）。
     *
     * @param fn 业务逻辑，入参为当前登录用户
     * @return 空 Result 的 Mono
     */
    public static Mono<Result<Void>> run(Consumer<LoginUser> fn) {
        return execute(u -> {
            fn.accept(u);
            return null;
        }, null);
    }

    /**
     * 执行有返回值的业务逻辑并记录操作日志（用于增删改/授权类接口）。
     *
     * @param module 功能模块（如 用户管理）
     * @param type   操作类型
     * @param desc   操作描述
     * @param fn     业务逻辑，入参为当前登录用户
     * @param <T>    返回数据类型
     * @return Result 包装的 Mono
     */
    public static <T> Mono<Result<T>> call(String module, OperType type, String desc,
                                           Function<LoginUser, T> fn) {
        return execute(fn, new OperLogMeta(module, type, desc));
    }

    /**
     * 执行无返回值的业务逻辑并记录操作日志（用于删除类接口）。
     *
     * @param module 功能模块
     * @param type   操作类型
     * @param desc   操作描述
     * @param fn     业务逻辑，入参为当前登录用户
     * @return 空 Result 的 Mono
     */
    public static Mono<Result<Void>> run(String module, OperType type, String desc,
                                         Consumer<LoginUser> fn) {
        return execute(u -> {
            fn.accept(u);
            return null;
        }, new OperLogMeta(module, type, desc));
    }

    /**
     * 核心执行逻辑：取用户与访问者 IP（Reactor 上下文）→ 切换线程池 →
     * 写 ThreadLocal → 执行业务（含日志记录）→ 清理。
     */
    private static <T> Mono<Result<T>> execute(Function<LoginUser, T> fn, OperLogMeta logMeta) {
        return Mono.deferContextual(ctxView -> {
            // IP 与 traceId 由 ClientIpFilter / TraceFilter 写入上下文；此处读出后随业务线程传递（阻塞线程拿不到请求对象）
            String clientIp = ctxView.getOrDefault(ClientIpFilter.CONTEXT_KEY, null);
            String traceId = ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null);
            return SecurityUtil.currentUser()
                    .flatMap(user -> Mono.fromCallable(() -> doExecute(user, clientIp, traceId, fn, logMeta))
                            .subscribeOn(Schedulers.boundedElastic()));
        });
    }

    /**
     * 在阻塞线程上执行：业务异常原样抛出（由全局异常处理器转 Result），
     * 日志参数存在时成功失败均落库（含访问者 IP）。
     */
    private static <T> Result<T> doExecute(LoginUser user, String clientIp, String traceId,
                                           Function<LoginUser, T> fn, OperLogMeta logMeta) {
        UserContextHolder.set(user);
        IpContextHolder.set(clientIp);
        // MDC 随线程切换会丢失，切到业务线程后重新注入，业务日志即可带链路 traceId
        putTrace(traceId);
        try {
            T data = fn.apply(user);
            recordLog(logMeta, user, true, null);
            return Result.ok(data);
        } catch (RuntimeException e) {
            recordLog(logMeta, user, false, e.getMessage());
            throw e;
        } finally {
            UserContextHolder.clear();
            IpContextHolder.clear();
            MDC.remove(TraceFilter.MDC_KEY);
        }
    }

    /**
     * traceId 非空时写入 MDC（入口统一收口，避免各处重复判空）。
     *
     * @param traceId 链路跟踪ID（可为 null）
     */
    public static void putTrace(String traceId) {
        if (traceId != null) {
            MDC.put(TraceFilter.MDC_KEY, traceId);
        }
    }

    /**
     * 落操作日志（异步）：字段快照在当前线程构建后，DB 写入移交
     * boundedElastic 独立线程，不占用业务线程、不延迟接口响应；
     * 日志写入失败只告警，不影响业务结果。
     */
    private static void recordLog(OperLogMeta meta, LoginUser user, boolean success, String errorMsg) {
        if (meta == null) {
            return;
        }
        OperLog operLog = new OperLog();
        operLog.setTenantId(user.getTenantId());
        operLog.setModule(meta.module);
        operLog.setOperType(meta.type.name());
        operLog.setOperDesc(meta.desc);
        operLog.setStatus(success ? 1 : 0);
        // error_msg 列宽 512，框架异常信息（如 MyBatis 完整 SQL 报错）可能超长，
        // 不截断会导致日志落库失败、丢失失败原因记录
        operLog.setErrorMsg(truncate(errorMsg, 500));
        operLog.setOperName(user.getUsername());
        operLog.setIp(IpContextHolder.getIp());
        operLog.setOperTime(LocalDateTime.now());
        // 当前线程（业务线程）MDC 尚存，快照下来供异步落库线程重新注入，日志写入也带同一 traceId
        String traceId = MDC.get(TraceFilter.MDC_KEY);
        // fire-and-forget：异步落库，失败仅记录告警日志（订阅时才真正执行）
        Mono.fromRunnable(() -> {
                    putTrace(traceId);
                    try {
                        SpringContextHolder.getBean(OperLogMapper.class).insert(operLog);
                    } finally {
                        MDC.remove(TraceFilter.MDC_KEY);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(unused -> {
                }, e -> log.warn("record oper log failed, module={}, desc={}", meta.module, meta.desc, e));
    }

    /**
     * 操作日志参数三元组（内部使用）。
     */
    private record OperLogMeta(String module, OperType type, String desc) {
    }

    /** 超长文本截断（含省略标记），防止写入超过列宽报错；结果总长严格不超过 maxLen */
    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        // 截断位必须预留省略标记长度，否则拼接后总长反而超出列宽（此前 500+14=514 仍超 512 导致日志落库失败）
        String suffix = "...(truncated)";
        return text.substring(0, maxLen - suffix.length()) + suffix;
    }
}
