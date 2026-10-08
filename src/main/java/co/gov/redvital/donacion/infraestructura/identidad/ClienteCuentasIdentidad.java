package co.gov.redvital.donacion.infraestructura.identidad;

import co.gov.redvital.donacion.aplicacion.puerto.salida.CuentasIdentidadPort;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.excepcion.ServicioNoDisponible;
import co.gov.redvital.donacion.infraestructura.configuracion.PropiedadesRedVital;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de las operaciones internas de Identidad. Obtiene un token de
 * servicio con la credencial del cliente {@code donacion} y lo reutiliza hasta
 * un minuto antes de que venza. Plazo de tres segundos: si Identidad no
 * responde, el registro se rechaza con 503 y no se crea nada.
 */
@Component
class ClienteCuentasIdentidad implements CuentasIdentidadPort {

    private static final String YA_REGISTRADO =
            "No es posible crear el perfil con estos datos. Si ya tienes una cuenta, inicia sesión.";
    private static final String NO_DISPONIBLE =
            "No fue posible crear la cuenta en este momento. Inténtalo de nuevo en unos minutos.";

    private record TokenServicio(String valor, Instant vence) {
    }

    private final RestClient http;
    private final JsonMapper json;
    private final PropiedadesRedVital.Identidad config;
    private volatile TokenServicio token;

    ClienteCuentasIdentidad(PropiedadesRedVital propiedades, JsonMapper json) {
        this.config = propiedades.identidad();
        this.json = json;
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(config.plazo());
        fabrica.setReadTimeout(config.plazo());
        this.http = RestClient.builder().baseUrl(config.url()).requestFactory(fabrica).build();
    }

    @Override
    public UUID crearCuentaDonante(String correo, String credencial, String nombre, String claveIdempotencia) {
        try {
            String cuerpo = json.writeValueAsString(Map.of("correo", correo, "credencial", credencial, "nombre", nombre));
            String respuesta = http.post().uri("/internal/v1/cuentas-donante")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + tokenServicio())
                    .header("Idempotency-Key", claveIdempotencia)
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);
            return UUID.fromString(json.readTree(respuesta).path("usuario_id").asString());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(409))) {
                throw new Conflicto(YA_REGISTRADO);
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(401))) {
                token = null;
            }
            throw new ServicioNoDisponible(NO_DISPONIBLE);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ServicioNoDisponible(NO_DISPONIBLE);
        }
    }

    private String tokenServicio() {
        TokenServicio actual = token;
        if (actual != null && Instant.now().isBefore(actual.vence())) {
            return actual.valor();
        }
        String respuesta = http.post().uri("/internal/v1/sesiones/servicio")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(Map.of("cliente", config.cliente(), "secreto", config.secreto())))
                .retrieve()
                .body(String.class);
        JsonNode nodo = json.readTree(respuesta);
        long segundos = nodo.path("expira_en").asLong(900);
        TokenServicio nuevo = new TokenServicio(nodo.path("token_acceso").asString(),
                Instant.now().plusSeconds(Math.max(segundos - 60, 30)));
        token = nuevo;
        return nuevo.valor();
    }
}
