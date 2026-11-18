package cl.duoc.digitalfix.workorders.streaming;

import cl.duoc.digitalfix.workorders.streaming.config.WorkOrderKafkaProperties;
import cl.duoc.digitalfix.workorders.streaming.event.WorkOrderEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Envia el evento a {@code workorders.events} con clave = id de la orden.
 *
 * <p>El envio es asincrono: la respuesta HTTP no espera al broker. El resultado
 * se registra en el log con su particion y offset (trazable en Kafka UI) o, si
 * falla tras los reintentos del productor, como ERROR con el evento completo
 * para poder re-publicarlo. Antes de enviar se valida el contrato: un evento
 * invalido es un error de programacion y nunca sale hacia los consumidores.</p>
 */
@Component
@ConditionalOnProperty(prefix = "digitalfix.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaWorkOrderEventPublisher implements WorkOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaWorkOrderEventPublisher.class);

    private final KafkaTemplate<String, WorkOrderEvent> template;
    private final WorkOrderKafkaProperties props;
    private final Validator validator;

    public KafkaWorkOrderEventPublisher(KafkaTemplate<String, WorkOrderEvent> workOrderEventTemplate,
                                        WorkOrderKafkaProperties props, Validator validator) {
        this.template = workOrderEventTemplate;
        this.props = props;
        this.validator = validator;
    }

    @Override
    public void publish(WorkOrderEvent event) {
        Set<ConstraintViolation<WorkOrderEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            log.error("[KAFKA-NO-PUBLICADO] evento invalido {}: {}", event, violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining("; ")));
            return;
        }

        String topic = props.topics().workorderEvents();
        String key = String.valueOf(event.workOrderId());
        template.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA-NO-PUBLICADO] {} orden #{} eventId={} -> {}: {}", event.eventType(),
                        event.workOrderId(), event.eventId(), topic, ex.getMessage());
            } else {
                var meta = result.getRecordMetadata();
                log.info("[KAFKA] {} orden #{} -> {}-{}@{} (eventId={}, traceId={})", event.eventType(),
                        event.workOrderId(), meta.topic(), meta.partition(), meta.offset(),
                        event.eventId(), event.traceId());
            }
        });
    }
}
