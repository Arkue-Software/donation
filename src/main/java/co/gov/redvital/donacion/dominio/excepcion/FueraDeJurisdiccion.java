package co.gov.redvital.donacion.dominio.excepcion;

/**
 * Consulta explícita de un ámbito ajeno (DD, Tabla 24). Nombra solo el ámbito
 * propio del usuario: nombrar el ajeno ya filtraría información.
 */
public class FueraDeJurisdiccion extends ExcepcionDominio {

    public FueraDeJurisdiccion() {
        super("La información solicitada está fuera de tu jurisdicción. Tu consulta se limita a tu institución.");
    }
}
