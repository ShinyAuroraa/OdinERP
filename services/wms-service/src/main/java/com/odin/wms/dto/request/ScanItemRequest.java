package com.odin.wms.dto.request;

import java.util.UUID;

public record ScanItemRequest(String barcode, UUID scannedBy) {}
