package co.gov.redvital.donacion.dominio.modelo;

public enum EstadoAlerta {
    ABIERTA("abierta"),
    ATENDIDA("atendida"),
    CADUCADA("caducada");

    private final String codigo;

    EstadoAlerta(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static EstadoAlerta desdeCodigo(String codigo) {
        for (EstadoAlerta estado : values()) {
            if (estado.codigo.equals(codigo)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de alerta desconocido: " + codigo);
    }
}
