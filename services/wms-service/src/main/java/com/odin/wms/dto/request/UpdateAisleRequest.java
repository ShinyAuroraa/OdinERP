package com.odin.wms.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAisleRequest(
        @Size(max = 255) String name
) {}
