package cl.duoc.digitalfix.workorders;

import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import org.junit.jupiter.api.Test;

import static cl.duoc.digitalfix.workorders.entity.WorkOrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reglas de la maquina de estados del caso DigitalFix. */
class WorkOrderStatusTest {

    @Test
    void noSePuedeEjecutarSinAsignar() {
        assertFalse(CREADA.canTransitionTo(EN_EJECUCION));
    }

    @Test
    void flujoFelizCompleto() {
        assertTrue(CREADA.canTransitionTo(ASIGNADA));
        assertTrue(ASIGNADA.canTransitionTo(EN_DESPLAZAMIENTO));
        assertTrue(EN_DESPLAZAMIENTO.canTransitionTo(EN_EJECUCION));
        assertTrue(EN_EJECUCION.canTransitionTo(CERRADA));
    }

    @Test
    void estadosFinalesNoAvanzan() {
        for (WorkOrderStatus s : WorkOrderStatus.values()) {
            assertFalse(CERRADA.canTransitionTo(s));
            assertFalse(CANCELADA.canTransitionTo(s));
        }
    }

    @Test
    void noSeCancelaUnaOrdenEnEjecucion() {
        assertFalse(EN_EJECUCION.canTransitionTo(CANCELADA));
    }
}
