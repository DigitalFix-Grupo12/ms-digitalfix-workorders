package cl.duoc.digitalfix.workorders.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracion: el servicio de negocio emite el evento y la capa messaging publica solo si hubo commit. */
@SpringBootTest(properties = "digitalfix.services.audit-url=http://localhost:1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublishAfterCommitTest {

    static final List<Publication> SENT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingPublisherConfig {
        @Bean
        @Primary
        CommandPublisher recordingCommandPublisher() {
            return (publication, type, correlationId, traceId, payload) -> SENT.add(publication);
        }
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void clear() {
        SENT.clear();
    }

    @Test
    void crearYAsignarPublicaComandos() throws Exception {
        String id = mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Corte general\",\"clienteId\":\"ana@test\"}")
                .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(put("/api/workorders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASIGNADA\",\"tecnicoId\":\"tec1\"}")
                .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
            .andExpect(status().isOk());

        assertThat(SENT).containsExactly(Publication.EMAIL_CREATED, Publication.EMAIL_ASSIGNED, Publication.DISPATCH_TICKET);
    }

    @Test
    void transicionInvalidaNoPublicaNada() throws Exception {
        String id = mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Enchufe\",\"clienteId\":\"ana@test\"}")
                .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        SENT.clear();

        mvc.perform(put("/api/workorders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EN_EJECUCION\"}")
                .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
            .andExpect(status().isConflict());

        assertThat(SENT).isEmpty();
    }
}
