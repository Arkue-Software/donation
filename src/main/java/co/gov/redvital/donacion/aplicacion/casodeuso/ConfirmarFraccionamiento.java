package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import co.gov.redvital.donacion.dominio.servicio.MotorTransiciones;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Transición 1, para todas las unidades de la donación a la vez: o todas o ninguna (RF-03). */
public final class ConfirmarFraccionamiento {

    private static final String OPERACION = "confirmar_fraccionamiento";

    public record Comando(String observacionCodigo) {
    }

    private final RepositorioDonaciones donaciones;
    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final CatalogoPort catalogo;
    private final EjecutorIdempotente idempotente;
    private final ConsultasDeDonaciones consultas;
    private final Auditor auditor;
    private final Clock reloj;

    public ConfirmarFraccionamiento(RepositorioDonaciones donaciones, RepositorioUnidades unidades,
                                    RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                                    EjecutorIdempotente idempotente, ConsultasDeDonaciones consultas,
                                    Auditor auditor, Clock reloj) {
        this.donaciones = donaciones;
        this.unidades = unidades;
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.idempotente = idempotente;
        this.consultas = consultas;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public EjecutorIdempotente.Resultado<DonacionRegistrada> ejecutar(Actor actor, UUID donacionId, Comando c,
                                                                      String clave, ContextoPeticion ctx) {
        UUID institucion = actor.institucion();
        if (c.observacionCodigo() != null && !catalogo.existeObservacion(c.observacionCodigo())) {
            throw new DatosInvalidos("El campo observacion_codigo no pertenece al catálogo.");
        }
        return idempotente.ejecutar(actor.sub(), clave, OPERACION + ":" + donacionId, c, donacionId,
                id -> {
                    var donacion = donaciones.buscar(id)
                            .filter(d -> d.institucionId().equals(institucion))
                            .orElseThrow(NoEncontrado::new);
                    List<Unidad> propias = unidades.deDonacionParaActualizar(id);
                    if (propias.isEmpty() || propias.stream().anyMatch(u -> u.estado() != CodigoEstado.CAPTADA)) {
                        throw new Conflicto("Todas las unidades de la donación deben estar captadas para fraccionarla.");
                    }
                    Instant ahora = reloj.instant();
                    var motor = new MotorTransiciones(catalogo.matriz());
                    for (Unidad u : propias) {
                        var evento = motor.transitar(u, CodigoEstado.FRACCIONADA, ActorTipo.USUARIO, actor.sub(),
                                c.observacionCodigo(), ctx.correlacionId(), ahora);
                        unidades.actualizarEstado(u, ahora);
                        eventos.insertar(evento);
                    }
                    auditor.permitido(actor, OPERACION, "donacion", donacion.id(), ctx);
                    return consultas.reconstruir(id);
                },
                consultas::reconstruir);
    }
}
