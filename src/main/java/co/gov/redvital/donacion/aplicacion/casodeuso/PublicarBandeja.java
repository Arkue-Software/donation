package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.PublicadorMensajesPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Publicador de la bandeja de salida (ADR-018, DD 5.3.6). Lee hasta cien
 * pendientes en orden de secuencia y marca publicado solo lo que el broker
 * confirmó. Si un evento falla, el lote se detiene en él: publicar los
 * siguientes rompería el orden de los que comparten su clave.
 */
public final class PublicarBandeja {

    public static final long CLAVE_BLOQUEO = 0x52564253L; // "RVBS"
    private static final int LOTE = 100;
    private static final Duration RETENCION = Duration.ofDays(7);
    private static final Duration RETENCION_CLAVE_NATURAL = Duration.ofDays(30);
    private static final Logger LOG = Logger.getLogger(PublicarBandeja.class.getName());

    private final BandejaSalidaPort bandeja;
    private final PublicadorMensajesPort publicador;
    private final BloqueoConsultivoPort bloqueo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public PublicarBandeja(BandejaSalidaPort bandeja, PublicadorMensajesPort publicador, BloqueoConsultivoPort bloqueo,
                           UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.bandeja = bandeja;
        this.publicador = publicador;
        this.bloqueo = bloqueo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /** @return eventos publicados en el ciclo */
    public int ejecutar() {
        return unidadDeTrabajo.ejecutar(() -> {
            if (!bloqueo.intentar(CLAVE_BLOQUEO)) {
                return 0;
            }
            int publicados = 0;
            for (var evento : bandeja.pendientes(LOTE)) {
                try {
                    publicador.publicar(evento);
                } catch (RuntimeException e) {
                    bandeja.registrarFallo(evento.id());
                    LOG.warning(() -> "El broker no confirmó el evento " + evento.id() + "; se reintentará: " + e.getMessage());
                    break;
                }
                Instant ahora = reloj.instant();
                bandeja.marcarPublicado(evento.id(), ahora,
                        ahora.plus(evento.conClaveNatural() ? RETENCION_CLAVE_NATURAL : RETENCION));
                publicados++;
            }
            return publicados;
        });
    }

    public long antiguedadPendienteMasViejo() {
        return bandeja.antiguedadPendienteMasViejo(reloj.instant());
    }
}
