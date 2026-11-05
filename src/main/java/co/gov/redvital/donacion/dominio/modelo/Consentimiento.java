package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;

/** Último registro de una finalidad. La historia completa es de solo anexado. */
public record Consentimiento(Finalidad finalidad, boolean otorgado, String versionAviso, Instant registradoEn) {
}
