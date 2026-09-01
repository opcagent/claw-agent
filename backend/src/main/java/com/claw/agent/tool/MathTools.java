package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;


import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

/**
 * 数学计算与编码工具集。
 * <p>
 * 提供基础计算、高级数学函数、哈希计算、Base64 编解码等功能。
 */
@Slf4j
@ToolSet(
    code = ToolCodes.MATH_TOOLS,
    name = "数学工具",
    description = "提供数学计算、哈希函数、Base64 编解码、单位换算、密码生成等功能",
    category = "utility",
    enabledByDefault = true,
    version = "1.0.0"
)
public class MathTools {

    private static final Random RANDOM = new Random();

    /**
     * 执行基础数学计算(支持加减乘除、括号)。
     *
     * @param expression 数学表达式(如:(10 + 5) * 2 / 3)
     * @return 计算结果
     */
    @Tool(name = "calculate", description = "执行数学计算,支持加减乘除和括号")
    public String calculate(
            @ToolParam(name = "expression", description = "数学表达式,如 (10 + 5) * 2 / 3") String expression) {
        try {
            // 使用 JavaScript 引擎进行安全计算
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
            
            if (engine == null) {
                return "错误: 计算引擎不可用";
            }
            
            // 只允许数字和运算符,防止代码注入
            if (!expression.matches("[\\d.+\\-*/()\\s]+")) {
                return "错误: 表达式包含非法字符,只允许数字、小数点、加减乘除和括号";
            }
            
            Object result = engine.eval(expression);
            return expression + " = " + result.toString();
        } catch (Exception e) {
            log.error("Calculation error: {}", expression, e);
            return "计算错误: " + e.getMessage();
        }
    }

    /**
     * 计算平方根。
     *
     * @param number 要计算平方根的数字
     * @return 平方根结果
     */
    @Tool(name = "sqrt", description = "计算一个数的平方根")
    public String sqrt(
            @ToolParam(name = "number", description = "要计算平方根的数字") double number) {
        if (number < 0) {
            return "错误: 不能对负数求平方根";
        }
        double result = Math.sqrt(number);
        return String.format("√%f = %.6f", number, result);
    }

    /**
     * 计算幂次方。
     *
     * @param base     底数
     * @param exponent 指数
     * @return 幂次方结果
     */
    @Tool(name = "power", description = "计算幂次方(base的exponent次方)")
    public String power(
            @ToolParam(name = "base", description = "底数") double base,
            @ToolParam(name = "exponent", description = "指数") double exponent) {
        double result = Math.pow(base, exponent);
        return String.format("%.2f ^ %.2f = %.6f", base, exponent, result);
    }

    /**
     * 计算三角函数(sin, cos, tan)。
     *
     * @param function 函数名称(sin/cos/tan)
     * @param angle    角度(度)
     * @return 三角函数值
     */
    @Tool(name = "trigonometric", description = "计算三角函数(sin, cos, tan),输入为角度(度)")
    public String trigonometric(
            @ToolParam(name = "function", description = "函数名称: sin, cos, tan") String function,
            @ToolParam(name = "angle", description = "角度(度)") double angle) {
        try {
            double radians = Math.toRadians(angle);
            double result;
            
            switch (function.toLowerCase()) {
                case "sin":
                    result = Math.sin(radians);
                    break;
                case "cos":
                    result = Math.cos(radians);
                    break;
                case "tan":
                    result = Math.tan(radians);
                    break;
                default:
                    return "错误: 不支持的三角函数类型,请使用 sin, cos 或 tan";
            }
            
            return String.format("%s(%.2f°) = %.6f", function, angle, result);
        } catch (Exception e) {
            log.error("Trigonometric calculation error: function={}, angle={}", function, angle, e);
            return "三角函数计算错误: " + e.getMessage();
        }
    }

    /**
     * 计算对数。
     *
     * @param number 真数
     * @param base   底数(可选,默认为自然对数 e)
     * @return 对数值
     */
    @Tool(name = "logarithm", description = "计算对数,支持自然对数和指定底数的对数")
    public String logarithm(
            @ToolParam(name = "number", description = "真数") double number,
            @ToolParam(name = "base", description = "底数(可选,默认为自然对数e)", required = false) Double base) {
        if (number <= 0) {
            return "错误: 真数必须大于0";
        }
        
        double result;
        if (base == null || base <= 0 || base == 1) {
            // 自然对数
            result = Math.log(number);
            return String.format("ln(%.6f) = %.6f", number, result);
        } else {
            // 换底公式: log_b(x) = ln(x) / ln(b)
            result = Math.log(number) / Math.log(base);
            return String.format("log_%.2f(%.6f) = %.6f", base, number, result);
        }
    }

    /**
     * 生成随机数。
     *
     * @param min 最小值(包含)
     * @param max 最大值(包含)
     * @return 随机整数
     */
    @Tool(name = "random_int", description = "生成指定范围内的随机整数")
    public String randomInt(
            @ToolParam(name = "min", description = "最小值(包含)") int min,
            @ToolParam(name = "max", description = "最大值(包含)") int max) {
        if (min > max) {
            return "错误: 最小值不能大于最大值";
        }
        int result = RANDOM.nextInt(max - min + 1) + min;
        return String.format("随机数(%d ~ %d): %d", min, max, result);
    }

    /**
     * 生成随机密码。
     *
     * @param length 密码长度(默认16)
     * @return 随机生成的密码字符串
     */
    @Tool(name = "generate_password", description = "生成随机密码,包含大小写字母、数字和特殊字符")
    public String generatePassword(
            @ToolParam(name = "length", description = "密码长度(默认16)", required = false) Integer length) {
        if (length == null || length < 8) {
            length = 16;
        }
        if (length > 128) {
            length = 128;
        }
        
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        
        String allChars = upperCase + lowerCase + digits + specialChars;
        
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(allChars.charAt(RANDOM.nextInt(allChars.length())));
        }
        
        return String.format("生成的密码(长度%d): %s\n注意: 请妥善保存此密码,它不会再次显示", length, password.toString());
    }

    /**
     * 计算 MD5 哈希值。
     *
     * @param input 输入字符串
     * @return MD5 哈希值(十六进制)
     */
    @Tool(name = "md5_hash", description = "计算字符串的 MD5 哈希值")
    public String md5Hash(
            @ToolParam(name = "input", description = "要计算哈希的字符串") String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return String.format("MD5(\"%s\") = %s", input, sb.toString());
        } catch (Exception e) {
            log.error("MD5 hash error", e);
            return "MD5 计算失败: " + e.getMessage();
        }
    }

    /**
     * 计算 SHA-256 哈希值。
     *
     * @param input 输入字符串
     * @return SHA-256 哈希值(十六进制)
     */
    @Tool(name = "sha256_hash", description = "计算字符串的 SHA-256 哈希值")
    public String sha256Hash(
            @ToolParam(name = "input", description = "要计算哈希的字符串") String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return String.format("SHA-256(\"%s\") = %s", input, sb.toString());
        } catch (Exception e) {
            log.error("SHA-256 hash error", e);
            return "SHA-256 计算失败: " + e.getMessage();
        }
    }

    /**
     * Base64 编码。
     *
     * @param input 要编码的字符串
     * @return Base64 编码后的字符串
     */
    @Tool(name = "base64_encode", description = "将字符串进行 Base64 编码")
    public String base64Encode(
            @ToolParam(name = "input", description = "要编码的字符串") String input) {
        try {
            String encoded = Base64.getEncoder().encodeToString(input.getBytes("UTF-8"));
            return String.format("Base64 编码:\n原文: %s\n结果: %s", input, encoded);
        } catch (Exception e) {
            log.error("Base64 encode error", e);
            return "Base64 编码失败: " + e.getMessage();
        }
    }

    /**
     * Base64 解码。
     *
     * @param input Base64 编码的字符串
     * @return 解码后的原始字符串
     */
    @Tool(name = "base64_decode", description = "将 Base64 编码的字符串解码")
    public String base64Decode(
            @ToolParam(name = "input", description = "Base64 编码的字符串") String input) {
        try {
            byte[] decoded = Base64.getDecoder().decode(input);
            String result = new String(decoded, "UTF-8");
            return String.format("Base64 解码:\n编码: %s\n结果: %s", input, result);
        } catch (Exception e) {
            log.error("Base64 decode error", e);
            return "Base64 解码失败: 输入的字符串不是有效的 Base64 编码";
        }
    }

    /**
     * 单位换算(长度、重量、温度等)。
     *
     * @param value      数值
     * @param fromUnit   源单位
     * @param toUnit     目标单位
     * @return 换算结果
     */
    @Tool(name = "unit_convert", description = "单位换算,支持长度(m/km/cm/mm/in/ft)、重量(kg/g/lb/oz)、温度(C/F/K)")
    public String unitConvert(
            @ToolParam(name = "value", description = "数值") double value,
            @ToolParam(name = "fromUnit", description = "源单位") String fromUnit,
            @ToolParam(name = "toUnit", description = "目标单位") String toUnit) {
        try {
            double result;
            String category;
            
            // 长度转换(统一转换为米)
            double meters = convertToMeters(value, fromUnit);
            if (meters != Double.MIN_VALUE) {
                result = convertFromMeters(meters, toUnit);
                category = "长度";
            } else {
                // 重量转换(统一转换为克)
                double grams = convertToGrams(value, fromUnit);
                if (grams != Double.MIN_VALUE) {
                    result = convertFromGrams(grams, toUnit);
                    category = "重量";
                } else {
                    // 温度转换
                    Double tempResult = convertTemperature(value, fromUnit, toUnit);
                    if (tempResult != null) {
                        return String.format("温度转换: %.2f %s = %.2f %s", value, fromUnit, tempResult, toUnit);
                    } else {
                        return "错误: 不支持的单位转换。支持的单位:\n" +
                                "- 长度: m, km, cm, mm, in(英寸), ft(英尺)\n" +
                                "- 重量: kg, g, lb(磅), oz(盎司)\n" +
                                "- 温度: C(摄氏度), F(华氏度), K(开尔文)";
                    }
                }
            }
            
            return String.format("%s转换: %.4f %s = %.4f %s", category, value, fromUnit, result, toUnit);
        } catch (Exception e) {
            log.error("Unit conversion error: {} {} -> {}", value, fromUnit, toUnit, e);
            return "单位转换失败: " + e.getMessage();
        }
    }

    // 辅助方法: 长度转换
    private double convertToMeters(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "m": return value;
            case "km": return value * 1000;
            case "cm": return value / 100;
            case "mm": return value / 1000;
            case "in": return value * 0.0254;
            case "ft": return value * 0.3048;
            default: return Double.MIN_VALUE;
        }
    }

    private double convertFromMeters(double meters, String unit) {
        switch (unit.toLowerCase()) {
            case "m": return meters;
            case "km": return meters / 1000;
            case "cm": return meters * 100;
            case "mm": return meters * 1000;
            case "in": return meters / 0.0254;
            case "ft": return meters / 0.3048;
            default: return Double.MIN_VALUE;
        }
    }

    // 辅助方法: 重量转换
    private double convertToGrams(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "g": return value;
            case "kg": return value * 1000;
            case "lb": return value * 453.592;
            case "oz": return value * 28.3495;
            default: return Double.MIN_VALUE;
        }
    }

    private double convertFromGrams(double grams, String unit) {
        switch (unit.toLowerCase()) {
            case "g": return grams;
            case "kg": return grams / 1000;
            case "lb": return grams / 453.592;
            case "oz": return grams / 28.3495;
            default: return Double.MIN_VALUE;
        }
    }

    // 辅助方法: 温度转换
    private Double convertTemperature(double value, String fromUnit, String toUnit) {
        double celsius;
        
        // 先转换为摄氏度
        switch (fromUnit.toUpperCase()) {
            case "C": celsius = value; break;
            case "F": celsius = (value - 32) * 5 / 9; break;
            case "K": celsius = value - 273.15; break;
            default: return null;
        }
        
        // 再从摄氏度转换为目标单位
        switch (toUnit.toUpperCase()) {
            case "C": return celsius;
            case "F": return celsius * 9 / 5 + 32;
            case "K": return celsius + 273.15;
            default: return null;
        }
    }
}
