package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Alerta;
import co.gov.redvital.donacion.dominio.modelo.EstadoAlerta;
import co.gov.redvital.donacion.dominio.modelo.TipoAlerta;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioAlertas {

    List<Alerta> deInstitucion(UUID institucionId, EstadoAlerta estado);

    Optional<Alerta> buscarParaActualizar(UUID id);

    List<Alerta> abiertas(TipoAlerta tipo);

    /** Abre la alerta, o actualiza el valor observado si ya hay una abierta para la misma situación. */
    void abrirOActualizar(Alerta alerta);

    void cerrar(UUID id, EstadoAlerta estado, Instant ahora, UUID atendidaPor);
}
