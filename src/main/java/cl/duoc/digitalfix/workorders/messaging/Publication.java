package cl.duoc.digitalfix.workorders.messaging;

/**
 * Casos de uso de publicacion. Cada valor tiene su ruta (exchange + routing key)
 * en application.yml bajo digitalfix.rabbitmq.publications (clave kebab-case:
 * EMAIL_ASSIGNED -> email-assigned). Aqui NO hay nombres de RabbitMQ.
 */
public enum Publication {
    /** Email al cliente: orden recibida. */
    EMAIL_CREATED,
    /** Email al cliente: orden asignada a un tecnico. */
    EMAIL_ASSIGNED,
    /** Email al cliente: tecnico en camino. */
    EMAIL_ENROUTE,
    /** Email al cliente: trabajo cerrado. */
    EMAIL_CLOSED,
    /** Email al cliente: orden cancelada. */
    EMAIL_CANCELLED,
    /** Ticket de despacho a terreno para el tecnico asignado. */
    DISPATCH_TICKET,
    /** Actualizacion del ticket de despacho (en camino, en ejecucion, cerrada...). */
    DISPATCH_STATUS,
    /** Generacion del informe tecnico / certificado de trabajo en PDF. */
    WORK_REPORT
}
