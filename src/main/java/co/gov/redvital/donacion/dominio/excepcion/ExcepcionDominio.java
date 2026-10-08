package co.gov.redvital.donacion.dominio.excepcion;

/**
 * Raíz de los errores del dominio. Cada subtipo corresponde a una entrada del
 * catálogo de errores del incremento de operaciones; el adaptador web los
 * traduce a problemas RFC 9457 sin que el dominio conozca HTTP.
 *
 * El detalle nombra el problema, nunca el valor recibido (DD, sección 12).
 */
public abstract class ExcepcionDominio extends RuntimeException {

    protected ExcepcionDominio(String detalle) {
        super(detalle);
    }
}
