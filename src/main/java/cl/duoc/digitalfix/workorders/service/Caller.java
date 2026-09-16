package cl.duoc.digitalfix.workorders.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Identidad de quien llama, propagada por el BFF en X-User-Name / X-User-Roles
 * (el BFF ya valido el JWT). Si no hay roles, la llamada es interna entre
 * microservicios (ej. ms-digitalfix-report) y tiene acceso completo: este
 * servicio no es alcanzable desde Internet.
 */
public record Caller(String username, Set<String> roles) {

    private static final Set<String> STAFF = Set.of("Admin", "Supervisor");

    public static Caller of(String username, String rolesHeader) {
        Set<String> roles = rolesHeader == null || rolesHeader.isBlank()
            ? Set.of()
            : Arrays.stream(rolesHeader.split(",")).map(String::trim)
                .filter(r -> !r.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new Caller(username == null || username.isBlank() ? "desconocido" : username.trim(), roles);
    }

    public boolean isInternal() {
        return roles.isEmpty();
    }

    /** Cliente sin rol de staff: solo puede ver y crear sus propias ordenes. */
    public boolean isCustomerOnly() {
        return roles.contains("Cliente") && roles.stream().noneMatch(STAFF::contains);
    }
}
