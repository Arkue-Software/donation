package co.gov.redvital.donacion.aplicacion.comun;

/**
 * Lo que la bitácora necesita de la petición: la correlación propagada por el
 * gateway (EC-20) y el origen de red.
 */
public record ContextoPeticion(String correlacionId, String origen) {

    /** Sujeto de idempotencia de una operación pública: el origen de red (DD 5.3.6). */
    public String sujetoAnonimo() {
        String valor = "origen:" + (origen == null ? "desconocido" : origen);
        return valor.length() > 64 ? valor.substring(0, 64) : valor;
    }
}
