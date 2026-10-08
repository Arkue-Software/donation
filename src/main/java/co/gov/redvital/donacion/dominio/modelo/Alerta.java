package co.gov.redvital.donacion.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/** Alerta de inventario. Tiene ciclo propio: no se borra al resolverse, se cierra. */
public record Alerta(UUID id, TipoAlerta tipo, UUID institucionId, String componente,
                     String grupoSanguineo, UUID unidadId, int valorObservado, EstadoAlerta estado,
                     Instant generadaEn, Instant cerradaEn, UUID atendidaPor) {
}
