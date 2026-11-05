package co.gov.redvital.donacion.dominio.excepcion;

import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;

/** La matriz cerrada no admite pasar del estado actual al pretendido (EC-04). */
public class TransicionNoPermitida extends Conflicto {

    private final CodigoEstado actual;
    private final CodigoEstado pretendido;

    public TransicionNoPermitida(CodigoEstado actual, CodigoEstado pretendido) {
        super("La unidad está en estado " + (actual == null ? "inicial" : actual.codigo())
                + " y no admite pasar a " + pretendido.codigo() + ".");
        this.actual = actual;
        this.pretendido = pretendido;
    }

    public CodigoEstado actual() {
        return actual;
    }

    public CodigoEstado pretendido() {
        return pretendido;
    }
}
