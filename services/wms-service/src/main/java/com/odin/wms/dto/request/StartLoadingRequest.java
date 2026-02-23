package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartLoadingRequest(
        @NotNull UUID operatorId
) {}
