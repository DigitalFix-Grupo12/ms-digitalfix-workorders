package cl.duoc.digitalfix.workorders.streaming.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros del productor Kafka ({@code digitalfix.kafka.*}): unica fuente del
 * nombre del topico, del origen y de la version del contrato del evento.
 */
@Validated
@ConfigurationProperties(prefix = "digitalfix.kafka")
public record WorkOrderKafkaProperties(
        boolean enabled,
        @NotBlank String source,
        @Min(1) int schemaVersion,
        @Valid @NotNull Topics topics) {

    public record Topics(@NotBlank String workorderEvents) {}
}
