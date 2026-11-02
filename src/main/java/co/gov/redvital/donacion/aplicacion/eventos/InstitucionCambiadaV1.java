package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * EV-07 {@code institution.changed}, versión 1, productor: Servicio
 * Institucional. Alta o cambio de una institución, con su municipio y su
 * ruta: Donación la guarda en su proyección para resolver la jurisdicción de
 * U5, U6 y U7 y para nombrar el banco, sin preguntarle a Institucional.
 */
public record InstitucionCambiadaV1(
        UUID institucionId,
        String codigo,
        String nombre,
        String tipo,
        String territorioCodigo,
        String territorioRuta,
        boolean vigente,
        Instant cambiadoEn) {

    public static final String TIPO = "institution.changed";
}
