package co.gov.redvital.donacion.dominio.modelo;

import java.util.UUID;

/** Fracción obtenible de una donación y su vida útil, que es dato y no constante. */
public record Componente(UUID id, String codigo, String nombre, int vidaUtilDias) {
}
