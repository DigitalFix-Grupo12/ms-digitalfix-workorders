package cl.duoc.digitalfix.workorders.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integracion con un broker Kafka embebido: cada hecho de la orden llega a
 * workorders.events con la clave correcta, el contrato completo y en orden.
 */
@SpringBootTest(properties = {
        "digitalfix.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = "workorders.events")
class WorkOrderEventStreamingTest {

    private static final String TOPIC = "workorders.events";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper mapper;

    private Consumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        Map<String, Object> config = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void cicloDeVidaDeLaOrdenSePublicaEnOrdenConClaveYContrato() throws Exception {
        String id = create("Corte general");
        changeStatus(id, "{\"status\":\"ASIGNADA\",\"tecnicoId\":\"tec-7\"}");
        changeStatus(id, "{\"status\":\"EN_DESPLAZAMIENTO\"}");
        changeStatus(id, "{\"status\":\"EN_EJECUCION\"}");
        changeStatus(id, "{\"status\":\"CERRADA\"}");

        List<ConsumerRecord<String, String>> records = readForKey(id, 5);
        List<String> types = new ArrayList<>();
        for (ConsumerRecord<String, String> r : records) {
            types.add(mapper.readTree(r.value()).get("eventType").asText());
        }
        assertThat(types).containsExactly("CREATED", "ASSIGNED", "STATUS_CHANGED", "STATUS_CHANGED", "CLOSED");
        // Misma clave -> misma particion: el orden de la orden se conserva
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.get(0).partition());

        JsonNode assigned = mapper.readTree(records.get(1).value());
        assertThat(assigned.get("eventId").asText()).isNotBlank();
        assertThat(assigned.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(assigned.get("source").asText()).isEqualTo("ms-digitalfix-workorders");
        assertThat(assigned.get("workOrderId").asLong()).isEqualTo(Long.parseLong(id));
        assertThat(assigned.get("technician").asText()).isEqualTo("tec-7");
        assertThat(assigned.get("previousStatus").asText()).isEqualTo("CREADA");
        assertThat(assigned.get("status").asText()).isEqualTo("ASIGNADA");
        assertThat(assigned.at("/actor/username").asText()).isEqualTo("sup@test");
        assertThat(assigned.at("/actor/role").asText()).isEqualTo("Supervisor");
        assertThat(assigned.get("occurredAt").asText()).endsWith("Z");
        // Sin headers de tipo de Java: el contrato es solo el JSON
        assertThat(records.get(1).headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    void transicionInvalidaNoPublicaEvento() throws Exception {
        String id = create("Enchufe quemado");
        readForKey(id, 1); // CREATED

        mvc.perform(put("/api/workorders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_EJECUCION\"}")
                        .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
                .andExpect(status().isConflict());

        var more = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
        assertThat(more.records(TOPIC)).noneMatch(r -> id.equals(r.key()));
    }

    private String create(String descripcion) throws Exception {
        return mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"" + descripcion + "\",\"clienteId\":\"ana@test\"}")
                        .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    private void changeStatus(String id, String body) throws Exception {
        mvc.perform(put("/api/workorders/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
                .andExpect(status().isOk());
    }

    private List<ConsumerRecord<String, String>> readForKey(String key, int expected) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (out.size() < expected && System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                if (key.equals(r.key())) out.add(r);
            }
        }
        assertThat(out).hasSize(expected);
        return out;
    }
}
