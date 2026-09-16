package cl.duoc.digitalfix.workorders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de creacion. Solo se aceptan los campos que el cliente puede definir. */
public record WorkOrderDto(
    @NotBlank @Size(max = 500) String descripcion,
    @NotBlank String clienteId
) {}
