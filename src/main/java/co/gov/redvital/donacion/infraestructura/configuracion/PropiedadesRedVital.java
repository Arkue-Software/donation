package co.gov.redvital.donacion.infraestructura.configuracion;

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
        Documento documento,
        @DefaultValue Avisos avisos,
        @DefaultValue Procesos procesos) {

    /**
     * Validación del token de Identidad. La llave pública llega como archivo
     * (secreto del despliegue): el servicio no se la pide a Identidad.
     */
    public record Jwt(String emisor, String audiencia, String llavePublica) {
    }

    /** Clave del HMAC del documento. Rotarla deja huérfanos los resúmenes existentes. */
    public record Documento(String claveHmac) {
    }

    public record Avisos(@DefaultValue("true") boolean habilitados) {
    }

    public record Procesos(@DefaultValue("true") boolean habilitados) {
    }
}
