package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.UUID;

/** Consumo idempotente (ADR-018). */
public interface EventosProcesadosPort {

    /** Falso si el evento ya se aplicó: es un duplicado y se descarta sin efecto. */
    boolean registrarSiNuevo(String grupoConsumidor, UUID eventoId, String tipo, Instant ahora, Instant expiraEn);

    int depurar(Instant ahora);
}
