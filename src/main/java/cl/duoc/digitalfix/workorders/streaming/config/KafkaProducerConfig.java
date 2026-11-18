package cl.duoc.digitalfix.workorders.streaming.config;

import cl.duoc.digitalfix.workorders.streaming.WorkOrderEventPublisher;
import cl.duoc.digitalfix.workorders.streaming.event.WorkOrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Productor Kafka de eventos de la orden.
 *
 * <ul>
 *   <li>Clave String = id de la orden: todos los eventos de una orden van a la
 *       misma particion y se consumen en el orden en que ocurrieron.</li>
 *   <li>Valor JSON del DTO {@link WorkOrderEvent} sin headers de tipo: el contrato
 *       es el JSON, no una clase Java de este servicio (cada consumidor tiene su DTO).</li>
 *   <li>acks=all + idempotencia + reintentos (spring.kafka.producer.*): no se pierde
 *       un evento confirmado ni se duplica por reintento del productor.</li>
 * </ul>
 * Las propiedades de conexion y rendimiento vienen de {@code spring.kafka.*}.
 */
@Configuration
@EnableConfigurationProperties(WorkOrderKafkaProperties.class)
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "digitalfix.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<String, WorkOrderEvent> workOrderEventTemplate(ProducerFactory<Object, Object> baseProducerFactory,
                                                                        ObjectMapper objectMapper) {
        JsonSerializer<WorkOrderEvent> serializer = new JsonSerializer<WorkOrderEvent>(objectMapper).noTypeInfo();
        DefaultKafkaProducerFactory<String, WorkOrderEvent> factory = new DefaultKafkaProducerFactory<>(
                baseProducerFactory.getConfigurationProperties(), new StringSerializer(), serializer);
        return new KafkaTemplate<>(factory);
    }

    /** Sin broker (tests o desarrollo con digitalfix.kafka.enabled=false): solo se registra en el log. */
    @Bean
    @ConditionalOnProperty(prefix = "digitalfix.kafka", name = "enabled", havingValue = "false")
    public WorkOrderEventPublisher disabledWorkOrderEventPublisher() {
        return event -> log.info("[KAFKA-DESHABILITADO] {} orden #{} (no se publica)",
                event.eventType(), event.workOrderId());
    }
}
