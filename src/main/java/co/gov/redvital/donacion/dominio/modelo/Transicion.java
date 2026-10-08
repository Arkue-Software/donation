package co.gov.redvital.donacion.dominio.modelo;

/** Una fila de la matriz cerrada de transiciones (DD V3.0, Tabla 20). */
public record Transicion(int numero, CodigoEstado origen, CodigoEstado destino, ActorTipo actorTipo) {
}
