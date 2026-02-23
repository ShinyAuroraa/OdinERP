package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTransferRequest(

        @NotNull(message = "sourceLocationId é obrigatório")
        UUID sourceLocationId,

        @NotNull(message = "destinationLocationId é obrigatório")
        UUID destinationLocationId,

        @NotNull(message = "productId é obrigatório")
        UUID productId,

        UUID lotId,

        @Min(value = 1, message = "quantity deve ser maior que zero")
        int quantity,

        String reason
) {}
