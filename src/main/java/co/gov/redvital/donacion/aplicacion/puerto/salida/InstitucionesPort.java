package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Instituciones, leídas de la proyección local que alimenta el evento EV-07
 * del Servicio Institucional. Donación no le pregunta nada a Institucional:
 * los servicios solo se comunican por Kafka.
 */
public interface InstitucionesPort {

    /** Instituciones vigentes dentro de la ruta, incluidos sus descendientes. */
    Set<UUID> dentroDe(String rutaTerritorial);

    /** Nombre de la institución, si la proyección ya la conoce. */
    Optional<String> nombre(UUID institucionId);
}
