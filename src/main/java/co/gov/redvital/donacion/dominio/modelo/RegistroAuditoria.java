package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Hecho de la bitácora del contexto de donación. Registra que se intentó una
 * acción y con qué resultado, nunca el dato al que se intentó acceder.
 */
public record RegistroAuditoria(
        String actorTipo,
        String actorId,
        String rol,
        String jurisdiccionSolicitada,
        String operacion,
        String recursoTipo,
        UUID recursoId,
        boolean permitido,
        String correlacionId,
        String origen,
        Instant ocurridoEn) {
}
