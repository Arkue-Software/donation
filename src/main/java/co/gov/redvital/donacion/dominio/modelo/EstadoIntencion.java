package co.gov.redvital.donacion.dominio.modelo;

public enum EstadoIntencion {
    PENDIENTE("pendiente"),
    ATENDIDA("atendida"),
    CADUCADA("caducada");

    private final String codigo;

    EstadoIntencion(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static EstadoIntencion desdeCodigo(String codigo) {
        for (EstadoIntencion estado : values()) {
            if (estado.codigo.equals(codigo)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de intención desconocido: " + codigo);
    }
}
