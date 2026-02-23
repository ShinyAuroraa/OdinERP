package com.odin.wms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReceiveFinishedGoodsRequest(
        @NotEmpty(message = "Ao menos um item é obrigatório")
        @Valid
        List<FinishedGoodsItem> items
) {
    public record FinishedGoodsItem(
            @NotNull(message = "productId obrigatório")
            UUID productId,

            @NotNull(message = "locationId obrigatório")
            UUID locationId,

            @Min(value = 1, message = "quantityReceived deve ser >= 1")
            int quantityReceived,

            UUID lotId
    ) {}
}
