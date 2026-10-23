package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * EV-02 {@code notice.requested}, versión 1. Clave de partición: el
 * destinatario. Lleva identificadores, nunca el correo ni el nombre: el
 * trabajador de Notificaciones resuelve el contacto por su propio canal. La
 * plantilla lleva los datos del tipo de aviso que no son personales (DD,
 * Tabla 18), con claves en snake_case; es opcional y puede ir vacía.
 */
public record AvisoSolicitadoV1(
        UUID avisoId,
        String tipoAviso,
        String destinatarioTipo,
        UUID destinatarioId,
        UUID campaniaId,
        Map<String, String> plantilla,
        Instant generadoEn) {

    public static final String TIPO = "notice.requested";
    public static final int VERSION = 1;
    public static final String AVISO_CAMPANIA_PUBLICADA = "campania_publicada";
    /** Confirmación de la cuenta recién creada (RF-01). No exige el consentimiento de avisos. */
    public static final String AVISO_CUENTA_CREADA = "cuenta_creada";
}
