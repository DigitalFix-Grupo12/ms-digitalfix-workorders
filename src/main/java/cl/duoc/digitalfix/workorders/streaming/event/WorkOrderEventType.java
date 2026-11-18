package cl.duoc.digitalfix.workorders.streaming.event;

/** Hechos del ciclo de vida de una orden de trabajo publicados en workorders.events. */
public enum WorkOrderEventType {
    CREATED,
    ASSIGNED,
    STATUS_CHANGED,
    CLOSED,
    CANCELLED
}
