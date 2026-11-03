package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Series agregadas para M7 (DD 13.4): conteos, nunca unidades ni donantes. */
public interface IndicadoresPort {

    enum Agrupacion { DIA, SEMANA, MES }

    record Periodo(LocalDate inicio, long donaciones, long unidadesGeneradas, long unidadesNoAptas,
                   long unidadesVencidas) {
    }

    record Existencias(UUID institucionId, String componente, String grupoSanguineo, long unidadesDisponibles,
                       long unidadesPorVencer, boolean bajoUmbral) {
    }

    /** Donaciones captadas entre las fechas (inclusive), en la zona del servicio. */
    List<Periodo> captacion(Set<UUID> instituciones, LocalDate desde, LocalDate hasta, Agrupacion agrupacion);

    List<Existencias> inventario(Set<UUID> instituciones, String componente, String grupoSanguineo, LocalDate hoy);
}
