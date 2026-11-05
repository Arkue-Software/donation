package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento del historial de una unidad. La observación es un código del catálogo
 * operativo, nunca texto libre, y no existe ningún campo para una causa clínica.
 */
public record EventoUnidad(
        UUID id,
        UUID unidadId,
        CodigoEstado estadoAnterior,
        CodigoEstado estadoNuevo,
        ActorTipo actorTipo,
        String actorId,
        UUID institucionId,
        String observacion,
        String correlacionId,
        Instant ocurridoEn) {
}
