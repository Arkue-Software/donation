package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import java.util.List;
import java.util.UUID;

public interface RepositorioEventosUnidad {

    void insertar(EventoUnidad evento);

    List<EventoUnidad> deUnidad(UUID unidadId);
}
