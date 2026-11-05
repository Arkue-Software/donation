package co.gov.redvital.donacion.dominio.modelo;

import java.util.UUID;

/**
 * Reconocimiento del catálogo cerrado. No tiene ningún atributo de valor,
 * saldo ni canje: la donación no se remunera (Decreto 1571 de 1993, EC-14).
 */
public record Reconocimiento(UUID id, String codigo, String nombre, String descripcion,
                             int criterioDonaciones, int orden) {
}
