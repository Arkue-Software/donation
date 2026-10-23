package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioConsentimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import java.util.Set;
import java.util.UUID;

/**
 * Contacto de un donante en el momento de entregarle un aviso (DD, Sección 13,
 * {@code GET /internal/v1/donantes/{id}/contacto}). Solo lo pide el trabajador
 * de Notificaciones, para que ningún correo viaje por el broker (PD-09).
 * Devuelve el correo y el nombre para el saludo; nada más.
 *
 * No es contactable el donante inactivo o sin correo de contacto, ni el que no
 * tiene vigente el consentimiento de avisos cuando el tipo lo exige. Así una
 * revocación surte efecto también sobre los avisos ya decididos y aún no
 * entregados.
 */
public final class ConsultarContactoDonante {

    /** Tipos de aviso a donantes del catálogo (DD, Tabla 18, más la confirmación de la cuenta). */
    private static final Set<String> TIPOS = Set.of("cuenta_creada", "campania_publicada", "elegibilidad_recuperada",
            "reconocimiento_obtenido", "convocatoria");
    /** Los que exigen el consentimiento de avisos vigente. */
    private static final Set<String> EXIGEN_CONSENTIMIENTO = Set.of("campania_publicada", "convocatoria");

    public record Contacto(boolean contactable, String correo, String nombre) {

        static Contacto ninguno() {
            return new Contacto(false, null, null);
        }
    }

    private final RepositorioDonantes donantes;
    private final RepositorioConsentimientos consentimientos;

    public ConsultarContactoDonante(RepositorioDonantes donantes, RepositorioConsentimientos consentimientos) {
        this.donantes = donantes;
        this.consentimientos = consentimientos;
    }

    public Contacto ejecutar(UUID donanteId, String tipoAviso) {
        if (tipoAviso == null || !TIPOS.contains(tipoAviso)) {
            throw new DatosInvalidos("El parámetro tipo no es un tipo de aviso a donantes.");
        }
        Donante donante = donantes.buscar(donanteId).orElseThrow(NoEncontrado::new);
        if (!donante.activo() || donante.correo() == null || donante.correo().isBlank()) {
            return Contacto.ninguno();
        }
        if (EXIGEN_CONSENTIMIENTO.contains(tipoAviso) && consentimientos.vigentes(donanteId).stream()
                .noneMatch(c -> c.finalidad() == Finalidad.AVISOS_CAMPANAS && c.otorgado())) {
            return Contacto.ninguno();
        }
        return new Contacto(true, donante.correo(), donante.nombre());
    }
}
