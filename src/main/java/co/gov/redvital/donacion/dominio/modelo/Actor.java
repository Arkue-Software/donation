package co.gov.redvital.donacion.dominio.modelo;

import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import java.util.UUID;

/**
 * Usuario que hace la petición, resuelto del token.
 *
 * @param sub            reivindicación {@code sub}: el identificador de la cuenta
 * @param rol            rol vigente en el token
 * @param institucionId  institución de la jurisdicción, si el ámbito es institucional
 * @param jurisdiccion   valor crudo de la reivindicación, para la bitácora
 */
public record Actor(String sub, Rol rol, UUID institucionId, String jurisdiccion) {

    /**
     * Institución del operador o del administrador de banco. Un token
     * institucional sin institución no recibe nada: falla cerrado.
     */
    public UUID institucion() {
        if (institucionId == null) {
            throw new OperacionNoPermitida();
        }
        return institucionId;
    }

    /** Cuenta del donante o del operador, que Identidad emite como UUID. */
    public UUID cuentaId() {
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new OperacionNoPermitida();
        }
    }
}
