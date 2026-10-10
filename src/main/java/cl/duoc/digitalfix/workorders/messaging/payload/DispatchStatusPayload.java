package cl.duoc.digitalfix.workorders.messaging.payload;

/** Avance del ticket de despacho (estado actual de la orden en terreno). */
public record DispatchStatusPayload(Long orderId, String tecnicoId, String status) {}
