package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Consentimiento;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Historia de consentimientos. Solo anexado: revocar agrega una fila, nunca borra. */
public interface RepositorioConsentimientos {

    void insertar(UUID donanteId, Finalidad finalidad, boolean otorgado, String versionAviso, Instant ahora);

    /** El último registro de cada finalidad. */
    List<Consentimiento> vigentes(UUID donanteId);
}
