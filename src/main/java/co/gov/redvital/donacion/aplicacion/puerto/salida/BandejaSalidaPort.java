package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bandeja de salida transaccional (ADR-018). */
public interface BandejaSalidaPort {

    record EventoSalida(UUID id, String tipo, int versionEsquema, String tema, String clave,
                        Object carga, String claveNatural, String correlacionId) {
    }

    record EventoPendiente(UUID id, String tipo, int versionEsquema, String tema, String clave,
                           String cargaJson, boolean conClaveNatural, String correlacionId, Instant creadoEn) {
    }

    /**
     * Escribe el evento en la misma transacción que el hecho. Si su clave natural
     * ya existe, el hecho ya generó su evento y no se duplica (IN-11).
     */
    void encolar(EventoSalida evento, Instant ahora);

    /** Pendientes en orden de secuencia. */
    List<EventoPendiente> pendientes(int limite);

    void marcarPublicado(UUID id, Instant publicadoEn, Instant expiraEn);

    void registrarFallo(UUID id);

    int depurar(Instant ahora);

    /** Antigüedad en segundos del pendiente más viejo, para la alerta de cinco minutos. */
    long antiguedadPendienteMasViejo(Instant ahora);
}
