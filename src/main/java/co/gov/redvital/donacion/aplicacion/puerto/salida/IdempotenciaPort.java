package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reconocimiento de reenvíos (EC-10). */
public interface IdempotenciaPort {

    record Operacion(String sujeto, String clave, String operacion, String huella,
                     UUID recursoId, int codigoEstado, Instant creadoEn, Instant expiraEn) {
    }

    /**
     * Reserva la clave. Devuelve falso si ya existía: en ese caso la petición es
     * un reenvío o una reutilización indebida de la clave.
     */
    boolean reservar(Operacion operacion);

    Optional<Operacion> buscar(String sujeto, String clave);

    int depurar(Instant ahora);
}
