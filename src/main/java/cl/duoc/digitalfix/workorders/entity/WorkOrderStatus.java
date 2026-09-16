package cl.duoc.digitalfix.workorders.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maquina de estados del caso DigitalFix:
 * CREADA -> ASIGNADA -> EN_DESPLAZAMIENTO -> EN_EJECUCION -> CERRADA
 * (CANCELADA posible antes de EN_EJECUCION). No se puede ejecutar sin asignar.
 */
public enum WorkOrderStatus {
    CREADA, ASIGNADA, EN_DESPLAZAMIENTO, EN_EJECUCION, CERRADA, CANCELADA;

    public Set<WorkOrderStatus> next() {
        return switch (this) {
            case CREADA -> EnumSet.of(ASIGNADA, CANCELADA);
            case ASIGNADA -> EnumSet.of(EN_DESPLAZAMIENTO, CANCELADA);
            case EN_DESPLAZAMIENTO -> EnumSet.of(EN_EJECUCION, CANCELADA);
            case EN_EJECUCION -> EnumSet.of(CERRADA);
            case CERRADA, CANCELADA -> EnumSet.noneOf(WorkOrderStatus.class);
        };
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        return next().contains(target);
    }

    public boolean isFinal() {
        return this == CERRADA || this == CANCELADA;
    }
}
