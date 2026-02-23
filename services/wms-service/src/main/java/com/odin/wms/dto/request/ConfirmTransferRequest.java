package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmTransferRequest(

        @NotNull(message = "confirmedBy é obrigatório")
        UUID confirmedBy
) {}
