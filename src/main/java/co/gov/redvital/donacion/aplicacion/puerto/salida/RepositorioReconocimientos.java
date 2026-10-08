package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface RepositorioReconocimientos {

    /** Código del reconocimiento y momento del otorgamiento. */
    Map<String, Instant> otorgados(UUID donanteId);

    void otorgar(UUID donanteId, UUID reconocimientoId, UUID donacionId, Instant ahora);
}
