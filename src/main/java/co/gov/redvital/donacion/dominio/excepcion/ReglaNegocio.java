package co.gov.redvital.donacion.dominio.excepcion;

/** Una regla del dominio impide completar la operación (422). */
public class ReglaNegocio extends ExcepcionDominio {

    private final String tipo;

    public ReglaNegocio(String tipo, String detalle) {
        super(detalle);
        this.tipo = tipo;
    }

    public String tipo() {
        return tipo;
    }
}
