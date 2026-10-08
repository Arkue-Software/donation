package co.gov.redvital.donacion.dominio.excepcion;

public class OperacionNoPermitida extends ExcepcionDominio {

    public OperacionNoPermitida() {
        super("El rol no autoriza esta operación.");
    }
}
