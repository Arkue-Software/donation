package co.gov.redvital.donacion.aplicacion.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * EV-01 {@code donation.completed}, versión 1. Clave de partición: donacion_id.
 * Contrato: contratos/asyncapi.yaml. No contiene datos personales ni el
 * resultado de tamizaje: Campañas solo cuenta donaciones (RF-25, D-09).
 */
public record DonacionCompletadaV1(
        UUID donacionId,
        UUID institucionId,
        UUID campaniaId,
        String tipoDonacion,
        Instant fechaCaptacion,
        int unidadesGeneradas) {

    public static final String TIPO = "donation.completed";
    public static final int VERSION = 1;
}
