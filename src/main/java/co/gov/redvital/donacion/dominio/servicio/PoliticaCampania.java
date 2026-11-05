package co.gov.redvital.donacion.dominio.servicio;

import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.modelo.CampaniaPublicada;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Decide si una donación se asocia a la campaña indicada, con la proyección
 * local que Donación construye de los eventos de Campañas.
 *
 * Una campaña desconocida o de otra institución no bloquea el registro: la
 * donación se registra sin campaña y se declara como no confirmada (EC-08).
 * Una campaña propia fuera de sus fechas sí es un error del operador.
 */
public final class PoliticaCampania {

    public record Decision(UUID campaniaId, boolean confirmada) {
    }

    public Decision decidir(UUID solicitada, Optional<CampaniaPublicada> proyeccion,
                            UUID institucionOperador, Instant captacion) {
        if (solicitada == null) {
            return new Decision(null, false);
        }
        if (proyeccion.isEmpty() || !proyeccion.get().institucionId().equals(institucionOperador)) {
            return new Decision(null, false);
        }
        CampaniaPublicada campania = proyeccion.get();
        if (!campania.vigenteEn(captacion)) {
            throw new Conflicto("La campaña indicada no está vigente en la fecha de captación.");
        }
        return new Decision(campania.campaniaId(), true);
    }
}
