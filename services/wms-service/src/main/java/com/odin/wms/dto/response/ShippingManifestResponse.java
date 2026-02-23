package com.odin.wms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ShippingManifestResponse(
        UUID shippingOrderId,
        String manifestJson,
        Instant manifestGeneratedAt,
        int itemCount
) {}
