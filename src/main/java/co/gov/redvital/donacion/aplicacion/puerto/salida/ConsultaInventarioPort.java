package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Existencia;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Existencias calculadas sobre unidad. Solo cuentan las unidades cuyo estado
 * tiene cuenta_disponible, aptas y no vencidas (EC-12).
 */
public interface ConsultaInventarioPort {

    List<Existencia> existencias(UUID institucionId, String componente, String grupoSanguineo, LocalDate hoy);

    /** Grupo nulo significa todos los grupos. */
    long disponibles(UUID institucionId, String componente, String grupoSanguineo, LocalDate hoy);
}
