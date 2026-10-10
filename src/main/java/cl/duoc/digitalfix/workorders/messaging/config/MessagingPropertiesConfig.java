package cl.duoc.digitalfix.workorders.messaging.config;

import cl.duoc.digitalfix.workorders.messaging.CommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra las propiedades de mensajeria y un publicador de respaldo cuando
 * RabbitMQ esta deshabilitado (digitalfix.rabbitmq.enabled=false, p. ej. tests
 * o desarrollo sin broker): los comandos se registran en el log y se descartan.
 */
@Configuration
@EnableConfigurationProperties(RabbitMessagingProperties.class)
public class MessagingPropertiesConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingPropertiesConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "digitalfix.rabbitmq", name = "enabled", havingValue = "false")
    public CommandPublisher disabledCommandPublisher() {
        return (publication, type, correlationId, traceId, payload) ->
            log.info("[MQ-DESHABILITADO] {} {} {} (no se publica)", publication, type, correlationId);
    }
}
