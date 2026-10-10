# ms-digitalfix-workorders (puerto 8082)

[![CI](https://github.com/DigitalFix-Grupo12/ms-digitalfix-workorders/actions/workflows/ci.yml/badge.svg)](https://github.com/DigitalFix-Grupo12/ms-digitalfix-workorders/actions/workflows/ci.yml)

Propietario de las ordenes de trabajo y de la maquina de estados:
`CREADA -> ASIGNADA -> EN_DESPLAZAMIENTO -> EN_EJECUCION -> CERRADA` (`CANCELADA` antes de ejecutar).
No se puede ejecutar sin asignar tecnico. Cada cambio publica un evento en `ms-digitalfix-audit`
y, despues del commit, los comandos asincronos en RabbitMQ (ver *Mensajeria*).

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | /api/workorders?status= | Lista (filtro opcional por estado) |
| GET | /api/workorders/{id} | Detalle |
| POST | /api/workorders | Crea (`descripcion`, `clienteId`) -> 201 |
| PUT | /api/workorders/{id}/status | Cambia estado (`status`, `tecnicoId` al asignar); 409 si la transicion es invalida |

Variables: `AUDIT_URL` (default `http://localhost:8085`), `RABBITMQ_ADDRESSES` (default
`localhost:5672,localhost:5673`), `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_ENABLED` (default `true`).

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
                                         |-> ms-digitalfix-workorders :8082 -> audit
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
