package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Umbral de escasez y de vencimiento próximo de una institución.
 * Un grupo sanguíneo nulo significa «cualquier grupo».
 */
public record Umbral(UUID institucionId, String componente, String grupoSanguineo,
                     int minimoUnidades, int diasPreviosVencimiento,
                     UUID actualizadoPor, Instant actualizadoEn) {
}
