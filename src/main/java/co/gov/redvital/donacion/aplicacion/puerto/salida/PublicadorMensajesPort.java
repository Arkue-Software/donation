package co.gov.redvital.donacion.aplicacion.puerto.salida;

/** Publica un evento en el broker y espera su confirmación. Lanza si el broker no confirma. */
public interface PublicadorMensajesPort {

    void publicar(BandejaSalidaPort.EventoPendiente evento);
}
