package cl.duoc.digitalfix.workorders.messaging.config;

import cl.duoc.digitalfix.workorders.messaging.Publication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.EnumMap;
import java.util.Map;

/**
 * Nombres centralizados de la mensajeria (prefijo digitalfix.rabbitmq en
 * application.yml). Se valida al arrancar: si falta una ruta para algun
 * caso de uso, el servicio no levanta (falla rapido en vez de perder mensajes).
 */
@Validated
@ConfigurationProperties(prefix = "digitalfix.rabbitmq")
public record RabbitMessagingProperties(
    boolean enabled,
    @NotBlank String source,
    @Valid @NotNull Exchanges exchanges,
    @NotNull Map<Publication, @Valid Route> publications
) {

    public RabbitMessagingProperties {
        Map<Publication, Route> copy = new EnumMap<>(Publication.class);
        if (publications != null) copy.putAll(publications);
        publications = copy;
        if (enabled) {
            for (Publication p : Publication.values()) {
                if (!publications.containsKey(p)) {
                    throw new IllegalStateException("Falta digitalfix.rabbitmq.publications."
                        + p.name().toLowerCase().replace('_', '-') + " en application.yml");
                }
            }
        }
    }

    /** Resuelve el nombre real del exchange de una ruta. */
    public String exchangeName(Route route) {
        return switch (route.exchange()) {
            case DIRECT -> exchanges.direct();
            case TOPIC -> exchanges.topic();
        };
    }

    public Route route(Publication publication) {
        return publications.get(publication);
    }

    public record Exchanges(@NotBlank String direct, @NotBlank String topic, @NotBlank String deadLetter) {}

    public record Route(@NotNull ExchangeKind exchange, @NotBlank String routingKey) {}

    public enum ExchangeKind { DIRECT, TOPIC }
}
