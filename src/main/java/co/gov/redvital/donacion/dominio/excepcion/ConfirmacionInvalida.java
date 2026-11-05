package co.gov.redvital.donacion.dominio.excepcion;

public class ConfirmacionInvalida extends ExcepcionDominio {

    public ConfirmacionInvalida() {
        super("La confirmación no coincide con el identificador del recurso.");
    }
}
