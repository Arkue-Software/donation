package co.gov.redvital.donacion.infraestructura.mensajeria;

/** Un evento que no cumple su contrato no se reintenta: va directo al tema de mensajes muertos. */
class EventoMalFormado extends RuntimeException {

    EventoMalFormado(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
