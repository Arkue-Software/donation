package co.gov.redvital.donacion.dominio.modelo;

import java.time.LocalDate;

/**
 * Resultado de RF-02. Se calcula sin el veredicto de tamizaje: si lo usara,
 * la elegibilidad revelaría indirectamente el resultado (P-05, RNI-01).
 *
 * @param elegibleDesde fecha desde la que puede donar; nula si nunca podrá por edad
 * @param diasRestantes días que faltan; cero si ya es elegible
 */
public record Elegibilidad(boolean elegible, LocalDate elegibleDesde, Long diasRestantes) {
}
