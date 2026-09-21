package com.icepark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IssueRequestDTO {

    /** 场次内被领用的那条“器材绑定”ID */
    @NotNull(message = "绑定器材不能为空")
    private Long sessionEquipmentId;

    @NotBlank(message = "游客凭证号不能为空")
    @Size(max = 64, message = "游客凭证号长度不能超过64")
    private String visitorId;

    @Size(max = 100, message = "游客姓名长度不能超过100")
    private String visitorName;

    @NotBlank(message = "游客年龄段不能为空")
    private String visitorAgeGroup;

    /** 现场实测气温（℃） */
    @NotNull(message = "现场实测气温不能为空")
    private BigDecimal measuredTemperature;

    /** 经办工作人员 */
    @NotBlank(message = "经办人不能为空")
    @Size(max = 100, message = "经办人长度不能超过100")
    private String operator;
}
