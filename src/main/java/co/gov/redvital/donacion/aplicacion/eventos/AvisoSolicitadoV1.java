package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * EV-02 {@code notice.requested}, versión 1. Clave de partición: el
 * destinatario. Lleva identificadores, nunca el correo ni el nombre: el
 * trabajador de Notificaciones resuelve el contacto por su propio canal.
 */
public record AvisoSolicitadoV1(
        UUID avisoId,
        String tipoAviso,
        String destinatarioTipo,
        UUID destinatarioId,
        UUID campaniaId,
        Instant generadoEn) {

    public static final String TIPO = "notice.requested";
    public static final int VERSION = 1;
    public static final String AVISO_CAMPANIA_PUBLICADA = "campania_publicada";
}
