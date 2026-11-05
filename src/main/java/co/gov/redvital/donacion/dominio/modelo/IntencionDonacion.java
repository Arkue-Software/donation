package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro anónimo previo a la donación (RF-01, EC-17). No tiene nombre,
 * documento, correo ni teléfono, y esa ausencia es el cumplimiento de EC-17.
 */
public record IntencionDonacion(
        UUID id,
        String codigo,
        String grupoSanguineo,
        String municipioRuta,
        EstadoIntencion estado,
        Instant creadoEn,
        Instant expiraEn,
        Instant atendidaEn) {

    /** Estado efectivo: una intención pendiente cuyo plazo pasó ya está caducada. */
    public EstadoIntencion estadoEn(Instant ahora) {
        if (estado == EstadoIntencion.PENDIENTE && !expiraEn.isAfter(ahora)) {
            return EstadoIntencion.CADUCADA;
        }
        return estado;
    }
}
