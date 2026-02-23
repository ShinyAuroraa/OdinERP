package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPickingOrderRequest(
        @NotNull UUID operatorId,
        UUID zoneId
) {}
