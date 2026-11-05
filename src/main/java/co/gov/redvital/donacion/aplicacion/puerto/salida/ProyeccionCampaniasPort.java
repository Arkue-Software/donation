package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.CampaniaPublicada;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Proyección local de campañas, alimentada solo por eventos de Kafka. */
public interface ProyeccionCampaniasPort {

    Optional<CampaniaPublicada> buscar(UUID campaniaId);

    /** Inserta o actualiza; una publicación más antigua que la guardada se ignora. */
    void guardar(CampaniaPublicada campania, UUID eventoId, Instant ahora);
}
