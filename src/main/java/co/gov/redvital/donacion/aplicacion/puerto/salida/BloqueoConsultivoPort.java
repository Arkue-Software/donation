package co.gov.redvital.donacion.aplicacion.puerto.salida;

/**
 * Bloqueo consultivo de la base, ligado a la transacción en curso. Garantiza
 * que un proceso programado corre en una sola instancia del servicio a la vez.
 */
public interface BloqueoConsultivoPort {

    boolean intentar(long clave);
}
