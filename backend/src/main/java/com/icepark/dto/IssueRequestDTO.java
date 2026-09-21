package com.icepark.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IssueRequestDTO {

    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;

    @NotBlank(message = "领装游客姓名/编号不能为空")
    private String visitorName;

    /** 游客本人的年龄段：CHILD 幼童 / TEEN 青少年 / ADULT 成人 */
    @NotBlank(message = "游客年龄段不能为空")
    private String visitorAgeGroup;

    /** 现场实测气温（摄氏度） */
    @NotNull(message = "实测气温不能为空")
    @DecimalMin(value = "-100", message = "气温数值不合法")
    @DecimalMax(value = "60", message = "气温数值不合法")
    private BigDecimal temperature;

    @NotBlank(message = "发装操作员不能为空")
    private String operator;
}
