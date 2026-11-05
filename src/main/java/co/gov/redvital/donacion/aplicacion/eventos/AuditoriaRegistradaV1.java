package co.gov.redvital.donacion.aplicacion.eventos;

import co.gov.redvital.donacion.dominio.modelo.RegistroAuditoria;
import java.time.Instant;
import java.util.UUID;

/**
 * EV-10 {@code audit.recorded}, versión 1. Cada registro de la serie de
 * auditoría de Donación sale también por Kafka, desde la misma transacción,
 * para que el Servicio Institucional componga la bitácora (ADR-016) sin
 * consultar a Donación. Lleva exactamente la estructura común de las series.
 */
public record AuditoriaRegistradaV1(
        UUID id,
        String actorTipo,
        String actorId,
        String rol,
        String jurisdiccionSolicitada,
        String operacion,
        String recursoTipo,
        UUID recursoId,
        String resultado,
        String correlacionId,
        String origen,
        Instant ocurridoEn) {

    public static final String TIPO = "audit.recorded";
    public static final int VERSION = 1;

    public static AuditoriaRegistradaV1 de(RegistroAuditoria r) {
        return new AuditoriaRegistradaV1(r.id(), r.actorTipo(), r.actorId(), r.rol(), r.jurisdiccionSolicitada(),
                r.operacion(), r.recursoTipo(), r.recursoId(), r.permitido() ? "permitido" : "denegado",
                r.correlacionId(), r.origen(), r.ocurridoEn());
    }
}
