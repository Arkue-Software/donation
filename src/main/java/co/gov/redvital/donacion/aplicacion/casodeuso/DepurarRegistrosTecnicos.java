package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.EventosProcesadosPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.IdempotenciaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import java.time.Clock;

/** Borra las filas técnicas vencidas: bandeja publicada, eventos procesados y claves de idempotencia. */
public final class DepurarRegistrosTecnicos {

    private final BandejaSalidaPort bandeja;
    private final EventosProcesadosPort procesados;
    private final IdempotenciaPort idempotencia;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public DepurarRegistrosTecnicos(BandejaSalidaPort bandeja, EventosProcesadosPort procesados,
                                    IdempotenciaPort idempotencia, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.bandeja = bandeja;
        this.procesados = procesados;
        this.idempotencia = idempotencia;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    public int ejecutar() {
        return unidadDeTrabajo.ejecutar(() -> {
            var ahora = reloj.instant();
            return bandeja.depurar(ahora) + procesados.depurar(ahora) + idempotencia.depurar(ahora);
        });
    }
}
