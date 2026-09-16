# ms-digitalfix-workorders (puerto 8082)

Propietario de las ordenes de trabajo y de la maquina de estados:
`CREADA -> ASIGNADA -> EN_DESPLAZAMIENTO -> EN_EJECUCION -> CERRADA` (`CANCELADA` antes de ejecutar).
No se puede ejecutar sin asignar tecnico. Cada cambio publica un evento en `ms-digitalfix-audit`.

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | /api/workorders?status= | Lista (filtro opcional por estado) |
| GET | /api/workorders/{id} | Detalle |
| POST | /api/workorders | Crea (`descripcion`, `clienteId`) -> 201 |
| PUT | /api/workorders/{id}/status | Cambia estado (`status`, `tecnicoId` al asignar); 409 si la transicion es invalida |

Variable: `AUDIT_URL` (default `http://localhost:8085`).
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