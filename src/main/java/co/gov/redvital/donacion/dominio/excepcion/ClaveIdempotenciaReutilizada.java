package co.gov.redvital.donacion.dominio.excepcion;

public class ClaveIdempotenciaReutilizada extends ExcepcionDominio {

    public ClaveIdempotenciaReutilizada() {
        super("La clave de idempotencia ya se usó con una petición distinta.");
    }
}
