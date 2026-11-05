package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Donacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioDonaciones {

    void insertar(Donacion donacion);

    Optional<Donacion> buscar(UUID id);

    List<Donacion> deDonante(UUID donanteId);

    int contarDeDonante(UUID donanteId);
}
