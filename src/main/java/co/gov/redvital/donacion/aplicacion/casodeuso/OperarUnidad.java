package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.excepcion.TransicionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import co.gov.redvital.donacion.dominio.servicio.MotorTransiciones;
import co.gov.redvital.donacion.dominio.servicio.ReglaConfirmacion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Transiciones que ejecuta el operador sobre una unidad que su institución
 * custodia (DD, Tabla 20). Toda transición pasa por el motor; la que la matriz
 * no admite se rechaza con 409 y el intento queda en la bitácora.
 */
public final class OperarUnidad {

    public record Resultado(UUID unidadId, UUID eventoId, CodigoEstado estadoAnterior, CodigoEstado estadoNuevo,
                            Instant ocurridoEn) {

        static Resultado de(EventoUnidad e) {
            return new Resultado(e.unidadId(), e.id(), e.estadoAnterior(), e.estadoNuevo(), e.ocurridoEn());
        }
    }

    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final CatalogoPort catalogo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final EjecutorIdempotente idempotente;
    private final ReglaConfirmacion confirmacion;
    private final Auditor auditor;
    private final ZoneId zona;
    private final Clock reloj;

    public OperarUnidad(RepositorioUnidades unidades, RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                        UnidadDeTrabajo unidadDeTrabajo, EjecutorIdempotente idempotente,
                        ReglaConfirmacion confirmacion, Auditor auditor, ZoneId zona, Clock reloj) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.idempotente = idempotente;
        this.confirmacion = confirmacion;
        this.auditor = auditor;
        this.zona = zona;
        this.reloj = reloj;
    }

    /** Transición 2. */
    public Resultado iniciarTamizaje(Actor actor, UUID id, String observacion, ContextoPeticion ctx) {
        return transitar(actor, id, CodigoEstado.EN_TAMIZAJE, observacion, "iniciar_tamizaje", ctx, u -> { });
    }

    /**
     * Transiciones 3 y 4. Un único dato: apta o no apta. No existe ningún
     * campo de motivo, y la observación tampoco se admite aquí (EC-02).
     */
    public Resultado registrarTamizaje(Actor actor, UUID id, boolean apta, ContextoPeticion ctx) {
        return transitar(actor, id, apta ? CodigoEstado.DISPONIBLE : CodigoEstado.NO_APTA, null,
                "registrar_tamizaje", ctx, u -> { });
    }

    /** Transición 5: solo una unidad apta y vigente se compromete. */
    public Resultado reservar(Actor actor, UUID id, String observacion, ContextoPeticion ctx) {
        return transitar(actor, id, CodigoEstado.RESERVADA, observacion, "reservar_unidad", ctx, this::exigirVigente);
    }

    /** Transición 6. */
    public Resultado liberarReserva(Actor actor, UUID id, String observacion, ContextoPeticion ctx) {
        return transitar(actor, id, CodigoEstado.DISPONIBLE, observacion, "liberar_reserva", ctx, u -> { });
    }

    /** Transición 7, con confirmación explícita (EC-19, EC-41). */
    public EjecutorIdempotente.Resultado<Resultado> despachar(Actor actor, UUID id, String confirmacionTexto,
                                                              String observacion, String clave, ContextoPeticion ctx) {
        confirmacion.verificar(id, confirmacionTexto);
        return conAuditoriaDeRechazo(actor, id, "despachar_unidad", ctx, () ->
                idempotente.ejecutar(actor.sub(), clave, "despachar_unidad:" + id, List.of(id, String.valueOf(observacion)),
                        id, x -> aplicar(actor, id, CodigoEstado.DESPACHADA, observacion, "despachar_unidad", ctx,
                                this::exigirVigente),
                        this::ultimoEvento));
    }

    /** Transiciones 12 y 13. El protocolo es el mismo para no aptas y vencidas: no delata el origen. */
    public EjecutorIdempotente.Resultado<Resultado> disponer(Actor actor, UUID id, String confirmacionTexto,
                                                             String observacion, String clave, ContextoPeticion ctx) {
        confirmacion.verificar(id, confirmacionTexto);
        return conAuditoriaDeRechazo(actor, id, "disposicion_final", ctx, () ->
                idempotente.ejecutar(actor.sub(), clave, "disposicion_final:" + id, List.of(id, String.valueOf(observacion)),
                        id, x -> aplicar(actor, id, CodigoEstado.DESECHADA, observacion, "disposicion_final", ctx, u -> { }),
                        this::ultimoEvento));
    }

    private Resultado transitar(Actor actor, UUID id, CodigoEstado destino, String observacion, String operacion,
                                ContextoPeticion ctx, Consumer<Unidad> precondicion) {
        return conAuditoriaDeRechazo(actor, id, operacion, ctx, () ->
                unidadDeTrabajo.ejecutar(() -> aplicar(actor, id, destino, observacion, operacion, ctx, precondicion)));
    }

    /** El intento rechazado por la matriz se registra en su propia transacción, y se relanza. */
    private <T> T conAuditoriaDeRechazo(Actor actor, UUID id, String operacion, ContextoPeticion ctx,
                                        java.util.function.Supplier<T> trabajo) {
        try {
            return trabajo.get();
        } catch (TransicionNoPermitida e) {
            auditor.denegado(actor, operacion, "unidad", id, null, ctx);
            throw e;
        }
    }

    private Resultado aplicar(Actor actor, UUID id, CodigoEstado destino, String observacion, String operacion,
                              ContextoPeticion ctx, Consumer<Unidad> precondicion) {
        if (observacion != null && !catalogo.existeObservacion(observacion)) {
            throw new DatosInvalidos("El campo observacion_codigo no pertenece al catálogo.");
        }
        UUID institucion = actor.institucion();
        Unidad unidad = unidades.buscarParaActualizar(id)
                .filter(u -> u.custodiadaPor(institucion))
                .orElseThrow(NoEncontrado::new);
        precondicion.accept(unidad);
        Instant ahora = reloj.instant();
        EventoUnidad evento = new MotorTransiciones(catalogo.matriz())
                .transitar(unidad, destino, ActorTipo.USUARIO, actor.sub(), observacion, ctx.correlacionId(), ahora);
        unidades.actualizarEstado(unidad, ahora);
        eventos.insertar(evento);
        auditor.permitido(actor, operacion, "unidad", id, ctx);
        return Resultado.de(evento);
    }

    private void exigirVigente(Unidad unidad) {
        if (unidad.estado() == CodigoEstado.DISPONIBLE || unidad.estado() == CodigoEstado.RESERVADA) {
            LocalDate hoy = reloj.instant().atZone(zona).toLocalDate();
            if (unidad.venceAntesDe(hoy) || !Boolean.TRUE.equals(unidad.apta())) {
                throw new ReglaNegocio("unidad-no-vigente", "La unidad está vencida o no es apta.");
            }
        }
    }

    private Resultado ultimoEvento(UUID unidadId) {
        List<EventoUnidad> historial = eventos.deUnidad(unidadId);
        if (historial.isEmpty()) {
            throw new NoEncontrado();
        }
        return Resultado.de(historial.getLast());
    }
}
