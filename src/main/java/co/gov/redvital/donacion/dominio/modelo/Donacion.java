package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Acto de donación en un banco. Donante e intención son excluyentes (IN-07);
 * la institución sale siempre del token del operador, nunca del cuerpo.
 */
public record Donacion(
        UUID id,
        UUID donanteId,
        UUID intencionId,
        String tipoDonacion,
        UUID institucionId,
        UUID campaniaId,
        Instant fechaCaptacion,
        UUID operadorId) {

    public Donacion {
        if (donanteId != null && intencionId != null) {
            throw new IllegalArgumentException("Una donación no puede tener a la vez donante e intención (IN-07).");
        }
    }
}
