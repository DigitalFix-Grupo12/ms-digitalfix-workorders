package cl.duoc.digitalfix.workorders.streaming;

import cl.duoc.digitalfix.workorders.streaming.event.WorkOrderEvent;

/** Publica un hecho de la orden en el stream de eventos (workorders.events). */
@FunctionalInterface
public interface WorkOrderEventPublisher {

    void publish(WorkOrderEvent event);
}
