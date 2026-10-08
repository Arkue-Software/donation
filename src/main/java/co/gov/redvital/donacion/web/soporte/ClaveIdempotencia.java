package co.gov.redvital.donacion.web.soporte;

import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;

/** Cabecera Idempotency-Key: entre 1 y 64 caracteres visibles (EC-10). */
public final class ClaveIdempotencia {

    public static final String CABECERA = "Idempotency-Key";

    private ClaveIdempotencia() {
    }

    public static String validar(String clave) {
        if (clave == null || clave.isBlank() || clave.length() > 64 || !clave.matches("^[\\x21-\\x7E]+$")) {
            throw new DatosInvalidos("La cabecera Idempotency-Key debe tener entre 1 y 64 caracteres visibles.");
        }
        return clave;
    }
}
