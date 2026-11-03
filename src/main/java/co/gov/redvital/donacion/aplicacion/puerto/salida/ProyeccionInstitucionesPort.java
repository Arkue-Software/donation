package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.UUID;

/** Escritura de la proyección de instituciones (EV-07). */
public interface ProyeccionInstitucionesPort {

    record Institucion(UUID id, String codigo, String nombre, String tipo, String territorioCodigo,
                       String territorioRuta, boolean vigente, Instant cambiadoEn) {
    }

    /** Guarda la institución, salvo que la proyección ya tenga un cambio más reciente. */
    void guardar(Institucion institucion, UUID eventoId, Instant ahora);
}
