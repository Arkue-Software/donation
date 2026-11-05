package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Componente;
import co.gov.redvital.donacion.dominio.modelo.MatrizTransiciones;
import co.gov.redvital.donacion.dominio.modelo.Reconocimiento;
import java.util.List;
import java.util.Optional;

/** Catálogos cerrados de db_donacion. Se exponen por código, nunca por identificador. */
public interface CatalogoPort {

    Optional<Componente> componente(String codigo);

    boolean existeGrupoSanguineo(String codigo);

    boolean existeTipoDonacion(String codigo);

    boolean existeObservacion(String codigo);

    MatrizTransiciones matriz();

    List<Reconocimiento> reconocimientos();
}
