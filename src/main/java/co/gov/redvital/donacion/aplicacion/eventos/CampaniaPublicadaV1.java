package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.util.UUID;

/** EV-03 {@code campaign.published}, versión 1, tal como lo publica Campañas. */
public record CampaniaPublicadaV1(
        UUID campaniaId,
        UUID institucionId,
        String nombre,
        String sede,
        String territorioCodigo,
        String territorioRuta,
        Instant iniciaEn,
        Instant terminaEn,
        Instant publicadaEn) {

    public static final String TIPO = "campaign.published";
}
