package cl.duoc.digitalfix.workorders.repository;

import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    List<WorkOrder> findAllByOrderByIdDesc();
    List<WorkOrder> findByStatusOrderByIdDesc(WorkOrderStatus status);
    List<WorkOrder> findByClienteIdOrderByIdDesc(String clienteId);
    List<WorkOrder> findByClienteIdAndStatusOrderByIdDesc(String clienteId, WorkOrderStatus status);
}
