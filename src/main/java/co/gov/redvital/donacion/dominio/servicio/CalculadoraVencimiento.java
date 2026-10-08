package co.gov.redvital.donacion.dominio.servicio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Fecha de vencimiento: la de captación más la vida útil del componente. Se fija al crear y no cambia. */
public final class CalculadoraVencimiento {

    private final ZoneId zona;

    public CalculadoraVencimiento(ZoneId zona) {
        this.zona = zona;
    }

    public LocalDate vencimiento(Instant captacion, int vidaUtilDias) {
        return captacion.atZone(zona).toLocalDate().plusDays(vidaUtilDias);
    }
}
