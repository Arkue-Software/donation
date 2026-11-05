package co.gov.redvital.donacion.dominio.modelo;

public enum TipoAlerta {
    ESCASEZ("escasez"),
    VENCIMIENTO_PROXIMO("vencimiento_proximo");

    private final String codigo;

    TipoAlerta(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static TipoAlerta desdeCodigo(String codigo) {
        for (TipoAlerta tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de alerta desconocido: " + codigo);
    }
}
