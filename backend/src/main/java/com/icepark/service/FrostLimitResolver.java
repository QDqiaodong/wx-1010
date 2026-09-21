package com.icepark.service;

import com.icepark.entity.Equipment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析器材“能承受的最低气温”（℃）。
 * 优先使用结构化字段 minTemperature；为空时从耐寒规格文本中提取最严格（最小）的温度值兜底。
 */
@Component
public class FrostLimitResolver {

    /** 匹配形如 -20、-20.5、零下20、负15 的温度数字（取带负号或“零下/负”语义的数值） */
    private static final Pattern SIGNED_NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern MINUS_WORD = Pattern.compile("(?:零下|负)\\s*(\\d+(?:\\.\\d+)?)");

    /**
     * @return 生效的抗冻下限；无法确定时返回 null（由调用方决定如何拒绝）
     */
    public BigDecimal resolve(Equipment equipment) {
        if (equipment.getMinTemperature() != null) {
            return equipment.getMinTemperature();
        }
        return parseFromSpec(equipment.getFrostResistanceSpec());
    }

    private BigDecimal parseFromSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }

        BigDecimal min = null;

        Matcher wordMatcher = MINUS_WORD.matcher(spec);
        while (wordMatcher.find()) {
            BigDecimal v = new BigDecimal(wordMatcher.group(1)).negate();
            min = (min == null) ? v : min.min(v);
        }

        Matcher numMatcher = SIGNED_NUMBER.matcher(spec);
        while (numMatcher.find()) {
            BigDecimal v = new BigDecimal(numMatcher.group(1));
            // 仅采纳负温作为“下限”语义，避免把规格中的其它正数误判
            if (v.signum() < 0) {
                min = (min == null) ? v : min.min(v);
            }
        }
        return min;
    }
}
