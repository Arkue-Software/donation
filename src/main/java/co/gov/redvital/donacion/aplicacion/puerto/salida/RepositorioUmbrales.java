package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Umbral;
import java.util.List;
import java.util.UUID;

public interface RepositorioUmbrales {

    List<Umbral> deInstitucion(UUID institucionId);

    List<Umbral> todos();

    void guardar(Umbral umbral);
}
