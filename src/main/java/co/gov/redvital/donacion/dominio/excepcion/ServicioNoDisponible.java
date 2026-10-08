package co.gov.redvital.donacion.dominio.excepcion;

/** Una dependencia necesaria para completar la operación no respondió (503). */
public class ServicioNoDisponible extends ExcepcionDominio {

    public ServicioNoDisponible(String detalle) {
        super(detalle);
    }
}
