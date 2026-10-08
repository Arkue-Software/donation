package co.gov.redvital.donacion.dominio.modelo;

import java.util.Optional;

/** Rol tal como viaja en la reivindicación {@code role} del token de Identidad. */
public enum Rol {
    DONANTE("donante"),
    OPERADOR("operador"),
    ADMIN_BANCO("admin_banco"),
    COORDINADOR("coordinador"),
    ADMIN_NACIONAL("admin_nacional"),
    AUDITOR("auditor"),
    SERVICIO("servicio");

    private final String codigo;

    Rol(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static Optional<Rol> desdeCodigo(String codigo) {
        for (Rol rol : values()) {
            if (rol.codigo.equals(codigo)) {
                return Optional.of(rol);
            }
        }
        return Optional.empty();
    }
}
