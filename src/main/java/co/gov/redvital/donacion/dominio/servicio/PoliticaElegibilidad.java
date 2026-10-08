package co.gov.redvital.donacion.dominio.servicio;

import co.gov.redvital.donacion.dominio.modelo.Elegibilidad;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * RF-02, punto único de cambio de la regla de elegibilidad: edad entre 18 y
 * 65 años y al menos 56 días desde la última donación registrada. Nunca usa el
 * veredicto de tamizaje de las unidades (P-05).
 */
public final class PoliticaElegibilidad {

    public static final int INTERVALO_MINIMO_DIAS = 56;
    public static final int EDAD_MINIMA = 18;
    public static final int EDAD_MAXIMA = 65;

    public Elegibilidad evaluar(LocalDate fechaNacimiento, LocalDate ultimaDonacion, LocalDate hoy) {
        if (fechaNacimiento != null && !fechaNacimiento.plusYears(EDAD_MAXIMA + 1L).isAfter(hoy)) {
            return new Elegibilidad(false, null, null);
        }
        LocalDate desde = hoy;
        if (fechaNacimiento != null) {
            LocalDate mayoriaDeEdad = fechaNacimiento.plusYears(EDAD_MINIMA);
            if (mayoriaDeEdad.isAfter(desde)) {
                desde = mayoriaDeEdad;
            }
        }
        if (ultimaDonacion != null) {
            LocalDate trasIntervalo = ultimaDonacion.plusDays(INTERVALO_MINIMO_DIAS);
            if (trasIntervalo.isAfter(desde)) {
                desde = trasIntervalo;
            }
        }
        long dias = ChronoUnit.DAYS.between(hoy, desde);
        return new Elegibilidad(dias <= 0, desde, Math.max(dias, 0));
    }

    /** Fecha desde la que vuelve a ser elegible quien donó en {@code fecha}. */
    public LocalDate elegibleDesdeTrasDonar(LocalDate fecha) {
        return fecha.plusDays(INTERVALO_MINIMO_DIAS);
    }
}
