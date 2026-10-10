package cl.duoc.digitalfix.workorders.messaging;

/**
 * Contrato del campo "type" del envelope (lo interpreta ms-digitalfix-notify).
 * Es parte del formato del mensaje, no de la topologia de RabbitMQ.
 */
public enum CommandType {
    EMAIL,
    DISPATCH_TICKET,
    DISPATCH_STATUS,
    WORK_REPORT
}
