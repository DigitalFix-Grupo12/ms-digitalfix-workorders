package cl.duoc.digitalfix.workorders.messaging;

import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties;
import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementacion RabbitMQ de CommandPublisher. Arma el envelope, resuelve
 * exchange y routing key desde la configuracion y publica como mensaje
 * persistente con messageId = eventId (base de la idempotencia del consumidor).
 */
public class RabbitCommandPublisher implements CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitCommandPublisher.class);
    static final String TRACE_HEADER = "x-trace-id";

    private final RabbitTemplate template;
    private final RabbitMessagingProperties props;

    public RabbitCommandPublisher(RabbitTemplate template, RabbitMessagingProperties props) {
        this.template = template;
        this.props = props;
    }

    @Override
    public void publish(Publication publication, CommandType type, String correlationId, String traceId, Object payload) {
        Route route = props.route(publication);
        String exchange = props.exchangeName(route);
        String eventId = UUID.randomUUID().toString();
        CommandEnvelope envelope = new CommandEnvelope(type, eventId, Instant.now(), traceId,
            correlationId, props.source(), payload);
        try {
            template.convertAndSend(exchange, route.routingKey(), envelope, message -> {
                var p = message.getMessageProperties();
                p.setMessageId(eventId);
                p.setCorrelationId(correlationId);
                p.setType(type.name());
                p.setAppId(props.source());
                p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                p.setHeader(TRACE_HEADER, traceId);
                return message;
            }, new CorrelationData(eventId));
            log.info("[PUBLICADO] {} -> exchange={} routingKey={} type={} eventId={} correlationId={}",
                publication, exchange, route.routingKey(), type, eventId, correlationId);
        } catch (AmqpException ex) {
            // La orden ya esta guardada: no se revierte. Queda registro para reproceso manual.
            log.error("[NO-PUBLICADO] {} exchange={} routingKey={} eventId={} correlationId={} causa={}",
                publication, exchange, route.routingKey(), eventId, correlationId, ex.getMessage());
        }
    }
}
