package co.gov.redvital.donacion.infraestructura.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia del servicio. Los secretos llegan como archivos en
 * /run/secrets (configtree) y nunca se escriben en el repositorio.
 */
@ConfigurationProperties(prefix = "redvital")
public record PropiedadesRedVital(
        @DefaultValue("America/Bogota") String zonaHoraria,
        Jwt jwt,
        Identidad identidad,
        Documento documento,
        @DefaultValue Avisos avisos,
        @DefaultValue Procesos procesos) {

    /** Validación del token de Identidad. Emisor y audiencia son los que Identidad firma hoy. */
    public record Jwt(String emisor, String audiencia, String jwksUri) {
    }

    /** Llamada interna a Identidad para crear la cuenta del donante. */
    public record Identidad(String url, @DefaultValue("donacion") String cliente, String secreto,
                            @DefaultValue("3s") Duration plazo) {
    }

    /** Clave del HMAC del documento. Rotarla deja huérfanos los resúmenes existentes. */
    public record Documento(String claveHmac) {
    }

    public record Avisos(@DefaultValue("true") boolean habilitados) {
    }

    public record Procesos(@DefaultValue("true") boolean habilitados) {
    }
}
