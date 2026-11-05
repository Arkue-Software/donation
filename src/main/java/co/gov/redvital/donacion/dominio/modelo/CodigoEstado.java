package co.gov.redvital.donacion.dominio.modelo;

/**
 * Los nueve estados de la unidad (DD V3.0, sección 10). Ninguno va acompañado
 * de una causa: {@code NO_APTA} es un veredicto, no un diagnóstico.
 */
public enum CodigoEstado {
    CAPTADA("captada"),
    FRACCIONADA("fraccionada"),
    EN_TAMIZAJE("en_tamizaje"),
    DISPONIBLE("disponible"),
    RESERVADA("reservada"),
    DESPACHADA("despachada"),
    NO_APTA("no_apta"),
    VENCIDA("vencida"),
    DESECHADA("desechada");

    private final String codigo;

    CodigoEstado(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static CodigoEstado desdeCodigo(String codigo) {
        for (CodigoEstado estado : values()) {
            if (estado.codigo.equals(codigo)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de unidad desconocido: " + codigo);
    }
}
