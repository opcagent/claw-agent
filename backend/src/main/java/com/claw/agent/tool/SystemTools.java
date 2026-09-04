package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 系统与时间工具集。
 * <p>
 * 提供时间查询、日期计算、系统信息等基础功能。
 */
@Slf4j
@ToolSet(
    code = ToolCodes.SYSTEM_TOOLS,
    name = "系统工具",
    description = "提供时间查询、日期计算、UUID 生成、系统信息等基础功能",
    category = "utility",
    enabledByDefault = true,
    version = "1.0.0"
)
public class SystemTools {

    /**
     * 获取当前系统时间(包含时区信息)。
     *
     * @return 格式化的当前时间字符串
     */
    @Tool(name = "get_current_time", description = "获取当前系统时间,包含日期、时间和时区信息")
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zonedNow = now.atZone(zone);
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return zonedNow.format(formatter);
    }

    /**
     * 获取指定时区的当前时间。
     *
     * @param timezone 时区名称(如: Asia/Shanghai, America/New_York)
     * @return 格式化的时间字符串
     */
    @Tool(name = "get_time_by_timezone", description = "获取指定时区的当前时间")
    public String getTimeByTimezone(
            @ToolParam(name = "timezone", description = "时区名称,如 Asia/Shanghai, America/New_York") String timezone) {
        try {
            ZoneId zone = ZoneId.of(timezone);
            ZonedDateTime zonedNow = ZonedDateTime.now(zone);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return zonedNow.format(formatter);
        } catch (Exception e) {
            log.error("Invalid timezone: {}", timezone, e);
            return "无效的时区: " + timezone + "。常见时区: Asia/Shanghai, America/New_York, Europe/London";
        }
    }

    /**
     * 计算两个日期之间的天数差。
     *
     * @param date1 第一个日期(格式: yyyy-MM-dd)
     * @param date2 第二个日期(格式: yyyy-MM-dd)
     * @return 天数差
     */
    @Tool(name = "days_between_dates", description = "计算两个日期之间的天数差")
    public String daysBetweenDates(
            @ToolParam(name = "date1", description = "第一个日期,格式 yyyy-MM-dd") String date1,
            @ToolParam(name = "date2", description = "第二个日期,格式 yyyy-MM-dd") String date2) {
        try {
            LocalDate d1 = LocalDate.parse(date1, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate d2 = LocalDate.parse(date2, DateTimeFormatter.ISO_LOCAL_DATE);
            long days = ChronoUnit.DAYS.between(d1, d2);
            return String.format("%s 到 %s 相差 %d 天", date1, date2, days);
        } catch (Exception e) {
            log.error("Date parsing error: {} or {}", date1, date2, e);
            return "日期格式错误,请使用 yyyy-MM-dd 格式";
        }
    }

    /**
     * 日期加减计算。
     *
     * @param date   基准日期(格式: yyyy-MM-dd)
     * @param days   要增加或减少的天数(负数表示减少)
     * @return 计算后的日期
     */
    @Tool(name = "add_days_to_date", description = "在指定日期上增加或减少天数")
    public String addDaysToDate(
            @ToolParam(name = "date", description = "基准日期,格式 yyyy-MM-dd") String date,
            @ToolParam(name = "days", description = "要增加或减少的天数(负数表示减少)") int days) {
        try {
            LocalDate baseDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate resultDate = baseDate.plusDays(days);
            return resultDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.error("Date calculation error: date={}, days={}", date, days, e);
            return "日期计算错误,请检查输入格式";
        }
    }

    /**
     * 生成唯一标识符(UUID)。
     *
     * @return UUID 字符串
     */
    @Tool(name = "generate_uuid", description = "生成一个唯一的 UUID v4 标识符")
    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 获取系统基本信息。
     *
     * @return 系统信息字符串
     */
    @Tool(name = "get_system_info", description = "获取当前系统的 Java 运行时环境信息")
    public String getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        StringBuilder sb = new StringBuilder();
        sb.append("系统信息:\n");
        sb.append("- Java 版本: ").append(System.getProperty("java.version")).append("\n");
        sb.append("- 操作系统: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("\n");
        sb.append("- 处理器数量: ").append(runtime.availableProcessors()).append("\n");
        sb.append("- JVM 最大内存: ").append(maxMemory).append(" MB\n");
        sb.append("- JVM 已用内存: ").append(usedMemory).append(" MB\n");
        sb.append("- JVM 空闲内存: ").append(freeMemory).append(" MB\n");
        
        return sb.toString();
    }

    /**
     * 将 Unix 时间戳转换为可读时间。
     *
     * @param timestamp Unix 时间戳(秒)
     * @return 格式化的时间字符串
     */
    @Tool(name = "timestamp_to_datetime", description = "将 Unix 时间戳转换为可读的日期时间")
    public String timestampToDateTime(
            @ToolParam(name = "timestamp", description = "Unix 时间戳(秒)") long timestamp) {
        try {
            Instant instant = Instant.ofEpochSecond(timestamp);
            ZonedDateTime dateTime = instant.atZone(ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return dateTime.format(formatter);
        } catch (Exception e) {
            log.error("Timestamp conversion error: {}", timestamp, e);
            return "时间戳转换失败,请确保是有效的 Unix 时间戳";
        }
    }

    /**
     * 获取星期几。
     *
     * @param date 日期(格式: yyyy-MM-dd)
     * @return 星期几的中文描述
     */
    @Tool(name = "get_day_of_week", description = "获取指定日期是星期几")
    public String getDayOfWeek(
            @ToolParam(name = "date", description = "日期,格式 yyyy-MM-dd") String date) {
        try {
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            
            String[] chineseDays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
            return date + " 是 " + chineseDays[dayOfWeek.getValue() - 1];
        } catch (Exception e) {
            log.error("Get day of week error: {}", date, e);
            return "日期格式错误,请使用 yyyy-MM-dd 格式";
        }
    }
}
