package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Pagina;
import co.gov.redvital.donacion.aplicacion.comun.ResolutorAlcance;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.util.List;
import java.util.UUID;

/**
 * Unidades en custodia de las instituciones que el usuario puede leer (RF-04):
 * la suya, o las de su territorio para U5 y U6, y para U7 la trazabilidad de
 * cualquiera (DD 15.5, grupos 4 y 5). Una unidad fuera del alcance responde
 * 404, igual que una inexistente.
 */
public final class ConsultasDeUnidades {

    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final CatalogoPort catalogo;
    private final ResolutorAlcance alcance;

    public ConsultasDeUnidades(RepositorioUnidades unidades, RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                               ResolutorAlcance alcance) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.alcance = alcance;
    }

    public Pagina<Unidad> listar(Actor actor, String estado, String componente, int pagina, int tamano) {
        CodigoEstado filtroEstado = null;
        if (estado != null) {
            try {
                filtroEstado = CodigoEstado.desdeCodigo(estado);
            } catch (IllegalArgumentException e) {
                throw new DatosInvalidos("El parámetro estado no es un estado válido.");
            }
        }
        if (componente != null && catalogo.componente(componente).isEmpty()) {
            throw new DatosInvalidos("El parámetro componente no es un componente válido.");
        }
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new DatosInvalidos("La paginación admite tamaños entre 1 y 100.");
        }
        return unidades.listar(alcance.de(actor).instituciones(), filtroEstado, componente, pagina, tamano);
    }

    public Unidad detalle(Actor actor, UUID id) {
        var unidad = unidades.buscar(id).orElseThrow(NoEncontrado::new);
        if (!alcance.de(actor).incluye(unidad.institucionCustodiaId())) {
            throw new NoEncontrado();
        }
        return unidad;
    }

    public List<EventoUnidad> eventos(Actor actor, UUID id) {
        detalle(actor, id);
        return eventos.deUnidad(id);
    }
}
