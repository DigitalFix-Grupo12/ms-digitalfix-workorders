package cl.duoc.digitalfix.workorders.config;

import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.repository.WorkOrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.time.Instant;

/** Datos de demo (H2 en memoria): se cargan solo si la tabla esta vacia. */
@Configuration
@Profile("!test")
public class DataSeeder {

    @Bean
    CommandLineRunner seedWorkOrders(WorkOrderRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            Instant now = Instant.now();
            repo.save(order("Corte de energía en sector norte", "cliente-001", null,
                WorkOrderStatus.CREADA, now.minus(Duration.ofHours(2)), null));
            repo.save(order("Revisión de transformador T-12", "cliente-002", "tecnico-01",
                WorkOrderStatus.EN_EJECUCION, now.minus(Duration.ofHours(7)), null));
            repo.save(order("Cambio de medidor domiciliario", "cliente-003", "tecnico-02",
                WorkOrderStatus.CERRADA, now.minus(Duration.ofHours(13)), now.minus(Duration.ofHours(12))));
        };
    }

    private WorkOrder order(String desc, String cliente, String tecnico, WorkOrderStatus st,
                            Instant created, Instant closed) {
        WorkOrder o = new WorkOrder();
        o.setDescripcion(desc);
        o.setClienteId(cliente);
        o.setTecnicoId(tecnico);
        o.setStatus(st);
        o.setCreatedAt(created);
        o.setClosedAt(closed);
        return o;
    }
}
