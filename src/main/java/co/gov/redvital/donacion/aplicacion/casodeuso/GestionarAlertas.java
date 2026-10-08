package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioAlertas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Alerta;
import co.gov.redvital.donacion.dominio.modelo.EstadoAlerta;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Alertas de la institución propia. Las lee el operador; las atiende el administrador de banco. */
public final class GestionarAlertas {

    private final RepositorioAlertas alertas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public GestionarAlertas(RepositorioAlertas alertas, UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        this.alertas = alertas;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public List<Alerta> listar(Actor actor, String estado) {
        EstadoAlerta filtro = null;
        if (estado != null) {
            try {
                filtro = EstadoAlerta.desdeCodigo(estado);
            } catch (IllegalArgumentException e) {
                throw new DatosInvalidos("El parámetro estado no es un estado de alerta válido.");
            }
        }
        return alertas.deInstitucion(actor.institucion(), filtro);
    }

    public Alerta atender(Actor actor, UUID id, ContextoPeticion ctx) {
        var institucion = actor.institucion();
        return unidadDeTrabajo.ejecutar(() -> {
            Alerta alerta = alertas.buscarParaActualizar(id)
                    .filter(a -> a.institucionId().equals(institucion))
                    .orElseThrow(NoEncontrado::new);
            if (alerta.estado() != EstadoAlerta.ABIERTA) {
                throw new Conflicto("La alerta ya está cerrada.");
            }
            alertas.cerrar(id, EstadoAlerta.ATENDIDA, reloj.instant(), actor.cuentaId());
            auditor.permitido(actor, "atender_alerta", "alerta", id, ctx);
            return alertas.buscarParaActualizar(id).orElseThrow(NoEncontrado::new);
        });
    }
}
