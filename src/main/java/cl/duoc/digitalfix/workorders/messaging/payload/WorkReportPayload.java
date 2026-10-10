package cl.duoc.digitalfix.workorders.messaging.payload;

import java.time.Instant;

/** Datos para el informe tecnico / certificado de trabajo (PDF). */
public record WorkReportPayload(Long orderId, String clienteId, String tecnicoId, String descripcion,
                                Instant createdAt, Instant closedAt, String closedBy) {}
