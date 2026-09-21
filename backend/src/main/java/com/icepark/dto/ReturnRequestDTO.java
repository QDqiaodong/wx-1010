package com.icepark.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReturnRequestDTO {

    @NotBlank(message = "归还操作员不能为空")
    private String operator;
}
