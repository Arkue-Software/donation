package co.gov.redvital.donacion.dominio.excepcion;

/**
 * El recurso no existe o está fuera del alcance del usuario. Las dos
 * situaciones responden igual a propósito: distinguirlas filtraría la
 * existencia de recursos de otra institución (DD, sección 12.3).
 */
public class NoEncontrado extends ExcepcionDominio {

    public NoEncontrado() {
        super("El recurso no existe o no está dentro de tu alcance.");
    }

    public NoEncontrado(String detalle) {
        super(detalle);
    }
}
