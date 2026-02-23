package com.odin.wms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SecondCountRequest(
        @NotNull @DecimalMin("0") BigDecimal secondCountedQty
) {}
