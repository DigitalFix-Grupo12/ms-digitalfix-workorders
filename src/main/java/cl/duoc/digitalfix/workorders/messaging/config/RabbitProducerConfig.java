package cl.duoc.digitalfix.workorders.messaging.config;

import cl.duoc.digitalfix.workorders.messaging.CommandPublisher;
import cl.duoc.digitalfix.workorders.messaging.RabbitCommandPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del PRODUCTOR (aislada de la logica de negocio).
 *
 * - Declara los exchanges que este servicio usa para publicar. Las colas,
 *   DLQ y bindings los declara el consumidor (ms-digitalfix-notify), dueno
 *   de esas colas. Declarar es idempotente: si ya existen con los mismos
 *   argumentos, RabbitMQ no hace nada.
 * - Serializa los mensajes como JSON.
 * - Registra callbacks de publisher confirms (el broker acepto o no el
 *   mensaje) y returns (el mensaje no tenia cola destino: routing key mal
 *   configurada o consumidor sin desplegar).
 */
@Configuration
@ConditionalOnProperty(prefix = "digitalfix.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitProducerConfig.class);

    @Bean
    public Declarables commandExchanges(RabbitMessagingProperties props) {
        DirectExchange direct = ExchangeBuilder.directExchange(props.exchanges().direct()).durable(true).build();
        TopicExchange topic = ExchangeBuilder.topicExchange(props.exchanges().topic()).durable(true).build();
        DirectExchange deadLetter = ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
        return new Declarables(direct, topic, deadLetter);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplateCustomizer publisherCallbacks() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                String id = correlation != null ? correlation.getId() : "?";
                if (ack) {
                    log.debug("[CONFIRM] broker acepto eventId={}", id);
                } else {
                    log.error("[CONFIRM-NACK] broker rechazo eventId={} causa={}", id, cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                "[RETURN] mensaje sin cola destino exchange={} routingKey={} replyCode={} motivo={} messageId={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(),
                returned.getReplyText(), returned.getMessage().getMessageProperties().getMessageId()));
        };
    }

    @Bean
    public CommandPublisher rabbitCommandPublisher(RabbitTemplate rabbitTemplate, RabbitMessagingProperties props) {
        return new RabbitCommandPublisher(rabbitTemplate, props);
    }
}
