package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.TransicionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import co.gov.redvital.donacion.dominio.servicio.MotorTransiciones;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * RF-18 y RNF-08: exclusión automática de unidades vencidas, sin intervención
 * manual. Se atribuye al sistema, nunca al último usuario conectado (EC-13).
 * Un bloqueo consultivo garantiza un solo ciclo a la vez entre instancias, y
 * una unidad que no admite la transición no detiene el lote.
 */
public final class ProcesarVencimientos {

    public static final long CLAVE_BLOQUEO = 0x52565643L; // "RVVC"
    private static final int LOTE = 500;
    private static final Logger LOG = Logger.getLogger(ProcesarVencimientos.class.getName());

    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final CatalogoPort catalogo;
    private final BloqueoConsultivoPort bloqueo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final ZoneId zona;
    private final Clock reloj;

    public ProcesarVencimientos(RepositorioUnidades unidades, RepositorioEventosUnidad eventos, CatalogoPort catalogo,
                                BloqueoConsultivoPort bloqueo, UnidadDeTrabajo unidadDeTrabajo, Auditor auditor,
                                ZoneId zona, Clock reloj) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.bloqueo = bloqueo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.zona = zona;
        this.reloj = reloj;
    }

    /** @return unidades retiradas en el ciclo */
    public int ejecutar() {
        return unidadDeTrabajo.ejecutar(() -> {
            if (!bloqueo.intentar(CLAVE_BLOQUEO)) {
                return 0;
            }
            Instant ahora = reloj.instant();
            LocalDate hoy = ahora.atZone(zona).toLocalDate();
            String correlacion = UUID.randomUUID().toString();
            var motor = new MotorTransiciones(catalogo.matriz());
            int retiradas = 0;
            for (Unidad unidad : unidades.vencidasParaActualizar(hoy, LOTE)) {
                try {
                    var evento = motor.transitar(unidad, CodigoEstado.VENCIDA, ActorTipo.SISTEMA,
                            Auditor.PROCESO_VENCIMIENTO, null, correlacion, ahora);
                    unidades.actualizarEstado(unidad, ahora);
                    eventos.insertar(evento);
                    auditor.sistema(Auditor.PROCESO_VENCIMIENTO, "exclusion_por_vencimiento", "unidad",
                            unidad.id(), correlacion);
                    retiradas++;
                } catch (TransicionNoPermitida e) {
                    LOG.warning(() -> "Unidad " + unidad.id() + " no admite el vencimiento: " + e.getMessage());
                }
            }
            return retiradas;
        });
    }
}
