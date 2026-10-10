package cl.duoc.digitalfix.workorders.messaging;

/**
 * Puerto de salida para comandos asincronos. La capa de negocio y el
 * traductor de eventos dependen de esta interfaz, no de RabbitTemplate.
 */
public interface CommandPublisher {

    /**
     * Publica un comando. Nunca lanza excepcion hacia el caller: si el broker
     * no esta disponible el error queda en el log y la operacion de negocio
     * (ya confirmada en la base de datos) no se revierte.
     *
     * @param publication   caso de uso; define exchange y routing key
     * @param type          tipo de comando (campo "type" del envelope)
     * @param correlationId agrupador de mensajes de una misma orden
     * @param traceId       identificador de la operacion de origen
     * @param payload       datos del comando (se serializa a JSON)
     */
    void publish(Publication publication, CommandType type, String correlationId, String traceId, Object payload);
}
