package cl.duoc.digitalfix.workorders.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Cliente HTTP hacia ms-digitalfix-audit. Best-effort: si audit esta caido
 * la operacion de negocio NO falla (se registra un WARN). En la evaluacion
 * de streaming este cliente se reemplaza por un producer Kafka.
 */
@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);
    private final RestClient client;

    public AuditClient(@Value("${digitalfix.services.audit-url}") String auditUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(1000);
        rf.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(auditUrl).requestFactory(rf).build();
    }

    public void record(String usuario, String accion, Long ordenId) {
        try {
            client.post()
                .uri("/api/audit")
                .body(Map.of(
                    "usuario", usuario,
                    "accion", accion,
                    "servicio", "ms-digitalfix-workorders",
                    "referencia", "orden#" + ordenId))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("No se pudo registrar evento de auditoria ({}): {}", accion, ex.getMessage());
        }
    }
}
