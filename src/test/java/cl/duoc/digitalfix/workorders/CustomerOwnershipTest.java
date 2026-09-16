package cl.duoc.digitalfix.workorders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autorizacion a nivel de dato: el rol Cliente solo accede a sus ordenes. */
@SpringBootTest(properties = "digitalfix.services.audit-url=http://localhost:1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerOwnershipTest {

    @Autowired
    private MockMvc mvc;

    private String createAs(String user, String roles, String clienteId) throws Exception {
        String body = clienteId == null
            ? "{\"descripcion\":\"Falla\"}"
            : "{\"descripcion\":\"Falla\",\"clienteId\":\"" + clienteId + "\"}";
        return mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Name", user).header("X-User-Roles", roles))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()
            .replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void clienteCreaOrdenASuNombreAunqueEnvieOtroClienteId() throws Exception {
        mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Corte\",\"clienteId\":\"otro\"}")
                .header("X-User-Name", "ana@test").header("X-User-Roles", "Cliente"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clienteId").value("ana@test"));
    }

    @Test
    void clienteSoloVeSusOrdenes() throws Exception {
        createAs("sup@test", "Supervisor", "pedro@test");
        createAs("luis@test", "Cliente", null);

        mvc.perform(get("/api/workorders").header("X-User-Name", "luis@test").header("X-User-Roles", "Cliente"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].clienteId", everyItem(is("luis@test"))));
    }

    @Test
    void clienteNoVeOrdenAjena() throws Exception {
        String id = createAs("sup@test", "Supervisor", "maria@test");
        mvc.perform(get("/api/workorders/" + id).header("X-User-Name", "intruso@test").header("X-User-Roles", "Cliente"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/workorders/" + id).header("X-User-Name", "maria@test").header("X-User-Roles", "Cliente"))
            .andExpect(status().isOk());
    }

    @Test
    void staffDebeIndicarCliente() throws Exception {
        mvc.perform(post("/api/workorders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Sin cliente\"}")
                .header("X-User-Name", "sup@test").header("X-User-Roles", "Supervisor"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void clienteNoCambiaEstados() throws Exception {
        String id = createAs("rosa@test", "Cliente", null);
        mvc.perform(put("/api/workorders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELADA\"}")
                .header("X-User-Name", "rosa@test").header("X-User-Roles", "Cliente"))
            .andExpect(status().isForbidden());
    }
}
