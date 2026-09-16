package cl.duoc.digitalfix.workorders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de creacion. clienteId es obligatorio para Admin/Supervisor; para el
 * rol Cliente se ignora y se usa la identidad del token (ver WorkOrderService).
 */
public record WorkOrderDto(
    @NotBlank @Size(max = 500) String descripcion,
    @Size(max = 255) String clienteId
) {}
