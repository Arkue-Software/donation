package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * EV-09 {@code indicators.updated}, versión 1. Fotografía de los agregados de
 * una institución para M7: la captación por día del periodo y sus existencias.
 * Solo conteos, nunca unidades ni donantes. Reemplaza lo que el Servicio
 * Institucional tenía de esa institución, así que reenviarlo no duplica nada.
 */
public record IndicadoresActualizadosV1(
        UUID institucionId,
        Instant generadoEn,
        LocalDate desde,
        LocalDate hasta,
        List<Dia> captacion,
        List<Existencias> inventario) {

    public static final String TIPO = "indicators.updated";
    public static final int VERSION = 1;

    public record Dia(LocalDate dia, long donaciones, long unidadesGeneradas, long unidadesNoAptas,
                      long unidadesVencidas) {
    }

    public record Existencias(String componente, String grupoSanguineo, long unidadesDisponibles,
                              long unidadesPorVencer, boolean bajoUmbral) {
    }
}
