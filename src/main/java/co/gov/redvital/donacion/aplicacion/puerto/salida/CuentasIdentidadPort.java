package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.util.UUID;

/**
 * Alta de la cuenta del donante en el Servicio de Identidad. Es la única
 * consulta síncrona que hace Donación a otro servicio: necesita la cuenta
 * para continuar (ADR-018).
 */
public interface CuentasIdentidadPort {

    /**
     * Crea la cuenta o devuelve la misma si la clave de idempotencia ya se usó.
     *
     * @return el identificador de la cuenta, que será la reivindicación sub
     */
    UUID crearCuentaDonante(String correo, String credencial, String nombre, String claveIdempotencia);
}
