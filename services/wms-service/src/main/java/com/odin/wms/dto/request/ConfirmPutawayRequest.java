package com.odin.wms.dto.request;

import java.util.UUID;

/**
 * Request para confirmar uma tarefa de putaway.
 * {@code confirmedLocationId} é opcional: se ausente, a localização sugerida
 * pelo motor FIFO/FEFO é utilizada. Se presente, substitui a sugestão —
 * o serviço valida que pertence ao mesmo warehouse da dock de origem.
 */
public record ConfirmPutawayRequest(
        UUID confirmedLocationId
) {
}
