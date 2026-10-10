package cl.duoc.digitalfix.workorders.messaging;

import java.time.Instant;

/**
 * Envelope comun de todos los comandos (caso DigitalFix, seccion 8):
 * type, eventId, timestamp, traceId, correlationId + payload.
 *
 * - eventId: UUID unico; el consumidor lo usa para idempotencia.
 * - correlationId: agrupa los mensajes de una misma orden ("orden-15").
 * - traceId: identifica la operacion que origino el mensaje (para logs).
 */
public record CommandEnvelope(
    CommandType type,
    String eventId,
    Instant timestamp,
    String traceId,
    String correlationId,
    String source,
    Object payload
) {}
