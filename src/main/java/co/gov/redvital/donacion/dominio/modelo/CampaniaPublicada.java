package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección local de una campaña, construida con el evento
 * {@code campaign-published}. Donación nunca consulta a Campañas por HTTP.
 */
public record CampaniaPublicada(
        UUID campaniaId,
        UUID institucionId,
        String nombre,
        String sede,
        String territorioCodigo,
        String territorioRuta,
        Instant iniciaEn,
        Instant terminaEn,
        Instant publicadaEn,
        String estado) {

    public boolean vigenteEn(Instant momento) {
        return "publicada".equals(estado) && !momento.isBefore(iniciaEn) && !momento.isAfter(terminaEn);
    }
}
