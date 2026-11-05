package co.gov.redvital.donacion.web.error;

/**
 * Problema RFC 9457 con los nombres del catálogo de errores del incremento:
 * los mismos que Identidad y Campañas.
 */
public record Problema(String tipo, String titulo, int estado, String detalle, String correlacionId) {

    public static final String TIPO_CONTENIDO = "application/problem+json";
}
