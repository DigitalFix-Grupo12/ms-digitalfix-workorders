package cl.duoc.digitalfix.workorders.dto;

import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeDto(
    @NotNull WorkOrderStatus status,
    String tecnicoId
) {}
