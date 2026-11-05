package co.gov.redvital.donacion.aplicacion.comun;

import co.gov.redvital.donacion.aplicacion.eventos.AuditoriaRegistradaV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.AuditoriaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.RegistroAuditoria;
import java.time.Clock;
import java.util.UUID;

/**
 * Escribe la bitácora con la misma forma desde todos los casos de uso. Cada
 * registro sale además por Kafka (EV-10), desde la misma transacción, para que
 * el Servicio Institucional componga la bitácora sin consultar a Donación.
 */
public final class Auditor {

    public static final String PROCESO_VENCIMIENTO = "proceso-vencimiento";
    public static final String PROCESO_ALERTAS = "proceso-alertas";

    private final AuditoriaPort auditoria;
    private final BandejaSalidaPort bandeja;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public Auditor(AuditoriaPort auditoria, BandejaSalidaPort bandeja, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.auditoria = auditoria;
        this.bandeja = bandeja;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /** Hecho permitido, dentro de la transacción del hecho. */
    public void permitido(Actor actor, String operacion, String recursoTipo, UUID recursoId, ContextoPeticion ctx) {
        anotar(registro(actor, operacion, recursoTipo, recursoId, true, null, ctx));
    }

    public void permitidoAnonimo(String operacion, String recursoTipo, UUID recursoId, ContextoPeticion ctx) {
        anotar(new RegistroAuditoria(UUID.randomUUID(), "anonimo", null, null, null, operacion, recursoTipo,
                recursoId, true, ctx.correlacionId(), ctx.origen(), reloj.instant()));
    }

    public void sistema(String proceso, String operacion, String recursoTipo, UUID recursoId, String correlacionId) {
        anotar(new RegistroAuditoria(UUID.randomUUID(), "sistema", proceso, null, null, operacion, recursoTipo,
                recursoId, true, correlacionId, null, reloj.instant()));
    }

    /**
     * Intento denegado, en su propia transacción: la operación se revierte,
     * pero el intento queda registrado con el mismo detalle que uno exitoso.
     */
    public void denegado(Actor actor, String operacion, String recursoTipo, UUID recursoId,
                         String jurisdiccionSolicitada, ContextoPeticion ctx) {
        unidadDeTrabajo.ejecutarEnNueva(() -> anotar(
                registro(actor, operacion, recursoTipo, recursoId, false, jurisdiccionSolicitada, ctx)));
    }

    private RegistroAuditoria registro(Actor actor, String operacion, String recursoTipo, UUID recursoId,
                                       boolean permitido, String jurisdiccionSolicitada, ContextoPeticion ctx) {
        return new RegistroAuditoria(UUID.randomUUID(), "usuario", actor.sub(), actor.rol().codigo(), jurisdiccionSolicitada,
                operacion, recursoTipo, recursoId, permitido, ctx.correlacionId(), ctx.origen(), reloj.instant());
    }

    /** El registro y su evento, en la misma transacción. */
    private void anotar(RegistroAuditoria r) {
        auditoria.registrar(r);
        // Partición por correlación: los hechos de una misma petición llegan en orden.
        bandeja.encolar(new BandejaSalidaPort.EventoSalida(UUID.randomUUID(), AuditoriaRegistradaV1.TIPO,
                AuditoriaRegistradaV1.VERSION, Temas.AUDITORIA_REGISTRADA, r.correlacionId(),
                AuditoriaRegistradaV1.de(r), null, r.correlacionId()), r.ocurridoEn());
    }
}
