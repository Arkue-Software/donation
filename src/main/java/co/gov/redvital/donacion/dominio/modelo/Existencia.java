package co.gov.redvital.donacion.dominio.modelo;

import java.time.LocalDate;
import java.util.UUID;

/** Existencias calculadas: unidades disponibles por institución, componente y grupo. */
public record Existencia(UUID institucionId, String componente, String grupoSanguineo,
                         long unidadesDisponibles, LocalDate vencimientoMasProximo) {
}
