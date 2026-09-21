package com.icepark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReturnRequestDTO {

    /** 经办归还的工作人员 */
    @NotBlank(message = "经办人不能为空")
    @Size(max = 100, message = "经办人长度不能超过100")
    private String operator;
}
