package cl.duoc.digitalfix.workorders.messaging;

import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.event.WorkOrderStatusChangedEvent;
import cl.duoc.digitalfix.workorders.messaging.payload.EmailPayload;
import cl.duoc.digitalfix.workorders.messaging.payload.WorkReportPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Que comandos genera cada transicion de la orden (rutas de mensajeria). */
class WorkOrderCommandTranslatorTest {

    record Sent(Publication publication, CommandType type, String correlationId, Object payload) {}

    private final List<Sent> sent = new ArrayList<>();
    private final WorkOrderCommandTranslator translator = new WorkOrderCommandTranslator(
        (publication, type, correlationId, traceId, payload) -> sent.add(new Sent(publication, type, correlationId, payload)));

    private static WorkOrderStatusChangedEvent event(WorkOrderStatus from, WorkOrderStatus to, String tecnico) {
        return new WorkOrderStatusChangedEvent(7L, from, to, "ana@test", tecnico, "Falla tablero",
            Instant.parse("2026-10-10T10:00:00Z"), to == WorkOrderStatus.CERRADA ? Instant.parse("2026-10-10T12:00:00Z") : null,
            "sup@test");
    }

    private List<Publication> publications() {
        return sent.stream().map(Sent::publication).toList();
    }

    @Test
    void creacionEnviaSoloEmailDeOrdenRecibida() {
        translator.on(event(null, WorkOrderStatus.CREADA, null));
        assertThat(publications()).containsExactly(Publication.EMAIL_CREATED);
        assertThat(sent.get(0).correlationId()).isEqualTo("orden-7");
        assertThat(((EmailPayload) sent.get(0).payload()).to()).isEqualTo("ana@test");
    }

    @Test
    void asignacionEnviaEmailYTicketDeDespacho() {
        translator.on(event(WorkOrderStatus.CREADA, WorkOrderStatus.ASIGNADA, "tec1"));
        assertThat(publications()).containsExactly(Publication.EMAIL_ASSIGNED, Publication.DISPATCH_TICKET);
        assertThat(sent.get(1).type()).isEqualTo(CommandType.DISPATCH_TICKET);
    }

    @Test
    void cierreEnviaEmailEstadoDeDespachoEInformePdf() {
        translator.on(event(WorkOrderStatus.EN_EJECUCION, WorkOrderStatus.CERRADA, "tec1"));
        assertThat(publications()).containsExactly(
            Publication.EMAIL_CLOSED, Publication.DISPATCH_STATUS, Publication.WORK_REPORT);
        WorkReportPayload report = (WorkReportPayload) sent.get(2).payload();
        assertThat(report.closedAt()).isNotNull();
        assertThat(report.closedBy()).isEqualTo("sup@test");
    }

    @Test
    void cancelacionSinTecnicoNoActualizaDespacho() {
        translator.on(event(WorkOrderStatus.CREADA, WorkOrderStatus.CANCELADA, null));
        assertThat(publications()).containsExactly(Publication.EMAIL_CANCELLED);
    }

    @Test
    void todosLosComandosDeUnaOrdenCompartenCorrelationYTrace() {
        translator.on(event(WorkOrderStatus.EN_EJECUCION, WorkOrderStatus.CERRADA, "tec1"));
        assertThat(sent).extracting(Sent::correlationId).containsOnly("orden-7");
    }
}
