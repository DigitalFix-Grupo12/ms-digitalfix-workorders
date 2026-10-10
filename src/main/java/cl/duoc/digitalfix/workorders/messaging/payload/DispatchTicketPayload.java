package cl.duoc.digitalfix.workorders.messaging.payload;

/** Ticket de despacho a terreno para el tecnico asignado. */
public record DispatchTicketPayload(Long orderId, String tecnicoId, String clienteId, String descripcion) {}
