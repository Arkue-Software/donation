package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Pagina;
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
 * Unidades en custodia de la institución del usuario (RF-04). Una unidad de
 * otra institución responde 404, igual que una inexistente.
 */
public final class ConsultasDeUnidades {

    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final CatalogoPort catalogo;

    public ConsultasDeUnidades(RepositorioUnidades unidades, RepositorioEventosUnidad eventos, CatalogoPort catalogo) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.catalogo = catalogo;
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
        return unidades.listar(actor.institucion(), filtroEstado, componente, pagina, tamano);
    }

    public Unidad detalle(Actor actor, UUID id) {
        return unidades.buscar(id).filter(u -> u.custodiadaPor(actor.institucion())).orElseThrow(NoEncontrado::new);
    }

    public List<EventoUnidad> eventos(Actor actor, UUID id) {
        detalle(actor, id);
        return eventos.deUnidad(id);
    }
}
