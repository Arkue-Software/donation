package co.gov.redvital.donacion.dominio.modelo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Matriz cerrada de transiciones. Es un dato (catálogo transicion_valida), no
 * código: toda transición ausente está prohibida (EC-04).
 */
public final class MatrizTransiciones {

    private final List<Transicion> transiciones;

    public MatrizTransiciones(Collection<Transicion> transiciones) {
        this.transiciones = List.copyOf(transiciones);
    }

    public Optional<Transicion> buscar(CodigoEstado origen, CodigoEstado destino, ActorTipo actorTipo) {
        return transiciones.stream()
                .filter(t -> t.origen() == origen && t.destino() == destino && t.actorTipo() == actorTipo)
                .findFirst();
    }

    public List<Transicion> transiciones() {
        return transiciones;
    }
}
