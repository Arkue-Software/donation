package co.gov.redvital.donacion.dominio.modelo;

import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Usuario que hace la petición, resuelto del token.
 *
 * @param sub            reivindicación {@code sub}: el identificador de la cuenta
 * @param rol            rol vigente en el token
 * @param institucionId  institución de la jurisdicción, si el ámbito es institucional
 * @param jurisdiccion   valor crudo de la reivindicación, para la bitácora
 */
public record Actor(String sub, Rol rol, UUID institucionId, String jurisdiccion) {

    private static final String PREFIJO_TERRITORIO = "territorio:";
    /** Forma de la ruta derivada del código DIVIPOLA (IN-13): /00, /00/05, /00/05/05001. */
    private static final Pattern RUTA = Pattern.compile("^/00(/(\\d{2})(/\\2\\d{3})?)?$");

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

    /**
     * Ruta territorial del coordinador, del administrador nacional o del
     * auditor ({@code territorio:/00/05}), o nulo si su ámbito es institucional.
     */
    public String rutaTerritorial() {
        if (jurisdiccion == null || !jurisdiccion.startsWith(PREFIJO_TERRITORIO)) {
            return null;
        }
        String ruta = jurisdiccion.substring(PREFIJO_TERRITORIO.length());
        return RUTA.matcher(ruta).matches() ? ruta : null;
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
