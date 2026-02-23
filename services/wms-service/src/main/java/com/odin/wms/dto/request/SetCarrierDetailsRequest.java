package com.odin.wms.dto.request;

import java.time.LocalDate;

public record SetCarrierDetailsRequest(
        String carrierName,
        String vehiclePlate,
        String driverName,
        String trackingNumber,
        LocalDate estimatedDelivery
) {}
