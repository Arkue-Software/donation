package co.gov.redvital.donacion.dominio.modelo;

/** Quién provoca una transición. El vencimiento se atribuye al sistema, nunca a un usuario (EC-13). */
public enum ActorTipo {
    USUARIO("usuario"),
    SISTEMA("sistema");

    private final String codigo;

    ActorTipo(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static ActorTipo desdeCodigo(String codigo) {
        for (ActorTipo tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de actor desconocido: " + codigo);
    }
}
