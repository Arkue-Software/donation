package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Qué instituciones tienen datos de M7 y cuál fue la última fotografía publicada de cada una. */
public interface PublicacionIndicadoresPort {

    /** Instituciones con donaciones, unidades o umbrales. */
    List<UUID> institucionesConDatos();

    Optional<String> huellaPublicada(UUID institucionId);

    void registrarPublicacion(UUID institucionId, String huella, Instant ahora);
}
