package com.icepark.util;

import com.icepark.exception.BusinessValidationException;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从器材"抗冻规格"文本中解析可承受下限气温。
 * 现场数据是自由文本（如"耐低温 -30℃"、"适用温度 -20°C ~ 10°C"、"-15度"），
 * 规则：抽取全部带符号/不带符号的整数或小数，取最小值作为下限；
 * 出现的正温度视为上限，不参与下限计算（以最小者为准天然成立）。
 */
public final class FrostSpecParser {

    /** 匹配 -30、-20.5、10 等数字（兼容全角负号－） */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private FrostSpecParser() {
    }

    public static BigDecimal parseLowerLimit(String frostResistanceSpec) {
        if (frostResistanceSpec == null || frostResistanceSpec.isBlank()) {
            throw new BusinessValidationException("该器材未配置抗冻规格，无法校验气温，请先在器材管理中补全");
        }

        String normalized = frostResistanceSpec.replace('－', '-').replace('–', '-').replace('—', '-');
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);

        BigDecimal lower = null;
        while (matcher.find()) {
            String token = matcher.group();
            // 排除规格文本中类似"GB-2024"年份数字被误吞的极端情况：
            // 要求数字前面紧邻的不是字母/数字
            int start = matcher.start();
            if (start > 0) {
                char prev = normalized.charAt(start - 1);
                if (Character.isLetterOrDigit(prev) && prev != '-') {
                    continue;
                }
            }
            BigDecimal value;
            try {
                value = new BigDecimal(token);
            } catch (NumberFormatException e) {
                continue;
            }
            if (value.compareTo(BigDecimal.valueOf(-100)) < 0
                    || value.compareTo(BigDecimal.valueOf(60)) > 0) {
                continue;
            }
            if (lower == null || value.compareTo(lower) < 0) {
                lower = value;
            }
        }

        if (lower == null) {
            throw new BusinessValidationException(
                    "器材抗冻规格[\"" + frostResistanceSpec + "\"]中未能解析出温度下限，请检查规格配置");
        }
        return lower;
    }
}
