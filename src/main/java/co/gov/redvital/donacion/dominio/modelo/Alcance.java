package co.gov.redvital.donacion.dominio.modelo;

import java.util.Set;
import java.util.UUID;

/**
 * Instituciones que el usuario puede leer (EC-01): la suya, si su ámbito es
 * institucional, o las que caen dentro de su ruta territorial, que resuelve el
 * Servicio Institucional (DD 13.2). Un alcance vacío no ve nada.
 */
public record Alcance(Set<UUID> instituciones) {

    public Alcance {
        instituciones = Set.copyOf(instituciones);
    }

    public static Alcance de(UUID institucion) {
        return new Alcance(Set.of(institucion));
    }

    public boolean incluye(UUID institucion) {
        return institucion != null && instituciones.contains(institucion);
    }

    public boolean vacio() {
        return instituciones.isEmpty();
    }
}
