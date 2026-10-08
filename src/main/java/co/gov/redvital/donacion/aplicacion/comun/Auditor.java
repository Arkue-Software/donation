package co.gov.redvital.donacion.aplicacion.comun;

import co.gov.redvital.donacion.aplicacion.puerto.salida.AuditoriaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.RegistroAuditoria;
import java.time.Clock;
import java.util.UUID;

/** Escribe la bitácora con la misma forma desde todos los casos de uso. */
public final class Auditor {

    public static final String PROCESO_VENCIMIENTO = "proceso-vencimiento";
    public static final String PROCESO_ALERTAS = "proceso-alertas";

    private final AuditoriaPort auditoria;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public Auditor(AuditoriaPort auditoria, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.auditoria = auditoria;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /** Hecho permitido, dentro de la transacción del hecho. */
    public void permitido(Actor actor, String operacion, String recursoTipo, UUID recursoId, ContextoPeticion ctx) {
        auditoria.registrar(registro(actor, operacion, recursoTipo, recursoId, true, null, ctx));
    }

    public void permitidoAnonimo(String operacion, String recursoTipo, UUID recursoId, ContextoPeticion ctx) {
        auditoria.registrar(new RegistroAuditoria("anonimo", null, null, null, operacion, recursoTipo,
                recursoId, true, ctx.correlacionId(), ctx.origen(), reloj.instant()));
    }

    public void sistema(String proceso, String operacion, String recursoTipo, UUID recursoId, String correlacionId) {
        auditoria.registrar(new RegistroAuditoria("sistema", proceso, null, null, operacion, recursoTipo,
                recursoId, true, correlacionId, null, reloj.instant()));
    }

    /**
     * Intento denegado, en su propia transacción: la operación se revierte,
     * pero el intento queda registrado con el mismo detalle que uno exitoso.
     */
    public void denegado(Actor actor, String operacion, String recursoTipo, UUID recursoId,
                         String jurisdiccionSolicitada, ContextoPeticion ctx) {
        unidadDeTrabajo.ejecutarEnNueva(() -> auditoria.registrar(
                registro(actor, operacion, recursoTipo, recursoId, false, jurisdiccionSolicitada, ctx)));
    }

    private RegistroAuditoria registro(Actor actor, String operacion, String recursoTipo, UUID recursoId,
                                       boolean permitido, String jurisdiccionSolicitada, ContextoPeticion ctx) {
        return new RegistroAuditoria("usuario", actor.sub(), actor.rol().codigo(), jurisdiccionSolicitada,
                operacion, recursoTipo, recursoId, permitido, ctx.correlacionId(), ctx.origen(), reloj.instant());
    }
}
