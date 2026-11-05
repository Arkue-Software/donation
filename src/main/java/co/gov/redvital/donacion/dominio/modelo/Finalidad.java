package co.gov.redvital.donacion.dominio.modelo;

import java.util.Optional;

/** Finalidades de tratamiento. Son autorizaciones distintas y nunca van en una sola casilla. */
public enum Finalidad {
    TRATAMIENTO_DATOS("tratamiento_datos"),
    AVISOS_CAMPANAS("avisos_campanas");

    private final String codigo;

    Finalidad(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static Optional<Finalidad> desdeCodigo(String codigo) {
        for (Finalidad finalidad : values()) {
            if (finalidad.codigo.equals(codigo)) {
                return Optional.of(finalidad);
            }
        }
        return Optional.empty();
    }
}
