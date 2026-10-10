package cl.duoc.digitalfix.workorders.messaging;

import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties;
import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties.ExchangeKind;
import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties.Exchanges;
import cl.duoc.digitalfix.workorders.messaging.config.RabbitMessagingProperties.Route;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitCommandPublisherTest {

    private static RabbitMessagingProperties props() {
        Map<Publication, Route> routes = new EnumMap<>(Publication.class);
        for (Publication p : Publication.values()) {
            routes.put(p, new Route(ExchangeKind.TOPIC, "email.x"));
        }
        routes.put(Publication.DISPATCH_TICKET, new Route(ExchangeKind.DIRECT, "dispatch.ticket"));
        return new RabbitMessagingProperties(true, "ms-test",
            new Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"), routes);
    }

    @Test
    void resuelveExchangeYRoutingKeyDesdeConfiguracionYMarcaElMensaje() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        new RabbitCommandPublisher(template, props())
            .publish(Publication.DISPATCH_TICKET, CommandType.DISPATCH_TICKET, "orden-3", "trace-1", Map.of("a", 1));

        ArgumentCaptor<CommandEnvelope> envelope = ArgumentCaptor.forClass(CommandEnvelope.class);
        ArgumentCaptor<MessagePostProcessor> mpp = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(eq("cmd.direct"), eq("dispatch.ticket"), envelope.capture(), mpp.capture(),
            any(CorrelationData.class));

        assertThat(envelope.getValue().type()).isEqualTo(CommandType.DISPATCH_TICKET);
        assertThat(envelope.getValue().source()).isEqualTo("ms-test");
        Message msg = mpp.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        MessageProperties p = msg.getMessageProperties();
        assertThat(p.getMessageId()).isEqualTo(envelope.getValue().eventId());
        assertThat(p.getCorrelationId()).isEqualTo("orden-3");
        assertThat(p.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat((String) p.getHeader("x-trace-id")).isEqualTo("trace-1");
    }

    @Test
    void brokerCaidoNoPropagaExcepcionAlNegocio() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new ConnectException("refused")))
            .when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        assertThatCode(() -> new RabbitCommandPublisher(template, props())
            .publish(Publication.EMAIL_CREATED, CommandType.EMAIL, "orden-1", "t", Map.of()))
            .doesNotThrowAnyException();
    }

    @Test
    void configuracionIncompletaFallaAlArrancar() {
        assertThatThrownBy(() -> new RabbitMessagingProperties(true, "ms",
            new Exchanges("d", "t", "x"), Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("digitalfix.rabbitmq.publications.");
    }
}
