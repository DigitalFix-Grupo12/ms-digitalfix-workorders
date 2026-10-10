package cl.duoc.digitalfix.workorders.messaging.payload;

/** Email/push al cliente. template: CREATED | ASSIGNED | ENROUTE | CLOSED | CANCELLED. */
public record EmailPayload(Long orderId, String to, String template, String status,
                           String tecnicoId, String descripcion) {}
