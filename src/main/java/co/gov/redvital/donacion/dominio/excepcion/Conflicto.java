package co.gov.redvital.donacion.dominio.excepcion;

/** El recurso existe pero su estado no admite la operación. */
public class Conflicto extends ExcepcionDominio {

    public Conflicto(String detalle) {
        super(detalle);
    }
}
