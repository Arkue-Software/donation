package co.gov.redvital.donacion.dominio.servicio;

import co.gov.redvital.donacion.dominio.excepcion.ConfirmacionInvalida;
import java.util.UUID;

/**
 * EC-19 y EC-41: despachar o desechar exige confirmar escribiendo el
 * identificador de la unidad. Evita actuar sobre la unidad equivocada.
 */
public final class ReglaConfirmacion {

    public void verificar(UUID recursoId, String confirmacion) {
        if (confirmacion == null || !confirmacion.trim().equalsIgnoreCase(recursoId.toString())) {
            throw new ConfirmacionInvalida();
        }
    }
}
