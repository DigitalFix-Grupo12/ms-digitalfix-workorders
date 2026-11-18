# ms-digitalfix-workorders (puerto 8082)

[![CI](https://github.com/DigitalFix-Grupo12/ms-digitalfix-workorders/actions/workflows/ci.yml/badge.svg)](https://github.com/DigitalFix-Grupo12/ms-digitalfix-workorders/actions/workflows/ci.yml)

Propietario de las ordenes de trabajo y de la maquina de estados:
`CREADA -> ASIGNADA -> EN_DESPLAZAMIENTO -> EN_EJECUCION -> CERRADA` (`CANCELADA` antes de ejecutar).
No se puede ejecutar sin asignar tecnico. Despues del commit, cada cambio se publica como **hecho** en
Kafka (`workorders.events`, ver *Streaming*) y como **comandos** asincronos en RabbitMQ (ver *Mensajeria*).

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | /api/workorders?status= | Lista (filtro opcional por estado) |
| GET | /api/workorders/{id} | Detalle |
| POST | /api/workorders | Crea (`descripcion`, `clienteId`) -> 201 |
| PUT | /api/workorders/{id}/status | Cambia estado (`status`, `tecnicoId` al asignar); 409 si la transicion es invalida |

Variables: `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092,localhost:9093,localhost:9094`),
`KAFKA_ENABLED` (default `true`), `RABBITMQ_ADDRESSES` (default `localhost:5672,localhost:5673`),
`RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_ENABLED` (default `true`).

## Streaming (productor Kafka)

`streaming.WorkOrderEventStreamer` escucha el evento de dominio **despues del commit** y publica un
`WorkOrderEvent` en `workorders.events` (3 particiones, RF 3). Lo consumen dos grupos independientes:
`report-kpis` (ms-digitalfix-report, KPIs en tiempo real) y `audit-timeline` (ms-digitalfix-audit).
Antes la auditoria se registraba por REST; ahora audit lee el mismo stream (sin acoplamiento ni duplicados).

| Hecho | eventType | Campos relevantes |
|---|---|---|
| orden creada | `CREATED` | `status`, `customer` |
| asignada | `ASSIGNED` | `technician`, `previousStatus` |
| en desplazamiento / en ejecucion | `STATUS_CHANGED` | `previousStatus`, `status` |
| cerrada | `CLOSED` | base del KPI de tiempo de resolucion |
| cancelada | `CANCELLED` | |

- **Clave = id de la orden**: sus eventos caen en la misma particion y se leen en orden.
- **Envelope**: `eventId` (idempotencia en los consumidores), `schemaVersion`, `occurredAt`, `traceId`,
  `source` y `actor` (`username`, `role`). JSON sin headers de tipo de Java (`JsonSerializer.noTypeInfo()`).
- **Productor**: `acks=all`, idempotente, `lz4`, `linger.ms=10`, `batch.size=32KB` (`spring.kafka.producer`).
  El envio es asincrono; el resultado (particion/offset o error) queda en el log (`[KAFKA]` / `[KAFKA-NO-PUBLICADO]`).
- Si la transaccion falla (ej. transicion invalida, 409) no se publica nada.

## Mensajeria (productor RabbitMQ)

El servicio de negocio solo emite un evento de dominio (`WorkOrderStatusChangedEvent`); el paquete
`messaging` lo traduce a comandos **despues del commit** (`@TransactionalEventListener`), asi nunca se
notifica algo que no quedo guardado. Exchanges y routing keys estan en `application.yml`
(`digitalfix.rabbitmq.publications`); el codigo no tiene nombres de RabbitMQ.

| Transicion | Comandos | Exchange · routing key |
|---|---|---|
| creada | email "orden recibida" | `cmd.topic` · `email.created` |
| ASIGNADA | email + ticket de despacho | `cmd.topic` · `email.assigned`, `cmd.direct` · `dispatch.ticket` |
| EN_DESPLAZAMIENTO | email "tecnico en camino" + avance | `cmd.topic` · `email.enroute`, `dispatch.status.update` |
| EN_EJECUCION | avance del ticket | `cmd.topic` · `dispatch.status.update` |
| CERRADA | email + avance + PDF | `cmd.topic` · `email.closed`, `cmd.direct` · `report.gen` |
| CANCELADA | email (+ avance si tenia tecnico) | `cmd.topic` · `email.cancelled` |

Mensajes JSON persistentes con envelope comun (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`,
`source`, `payload`), `messageId = eventId` y *publisher confirms* + *returns* (se registra en el log si el
broker rechaza un mensaje o si no tiene cola destino). Si RabbitMQ no esta disponible la orden igual se
guarda y el error queda en el log (`[NO-PUBLICADO]`).
## Arquitectura

```
Angular (MSAL) -> AWS API Gateway -> ms-digitalfix-bff :8080 (valida JWT Entra ID + rol)
                                         |-> ms-digitalfix-workorders :8082 -> Kafka workorders.events
                                         |-> ms-digitalfix-catalog    :8083
                                         |-> ms-digitalfix-report     :8084 -> workorders
                                         |-> ms-digitalfix-audit      :8085
```

Los microservicios de dominio **no validan JWT**: solo escuchan en la red interna
del host (el Security Group expone unicamente el 8080 del BFF). El BFF propaga la
identidad del usuario en el header `X-User-Name`.

## Ejecutar

```
mvn clean package
java -jar target/ms-digitalfix-workorders-0.0.1-SNAPSHOT.jar
```

Health: `GET /actuator/health`. Base de datos: H2 en memoria (se reinicia con el servicio).

## Perfiles de base de datos

| Perfil | Base de datos | Uso |
|---|---|---|
| (por defecto) | H2 en memoria | Desarrollo local y tests |
| `cloud` | Amazon RDS PostgreSQL, schema `workorders` | EC2 (`SPRING_PROFILES_ACTIVE=cloud`) |

Variables del perfil `cloud`: `DB_HOST`, `DB_PORT` (5432), `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`. En la EC2 se obtienen de SSM Parameter Store; nunca se guardan en el repo. El schema se crea al arrancar (`hibernate.hbm2ddl.create_namespaces`).
