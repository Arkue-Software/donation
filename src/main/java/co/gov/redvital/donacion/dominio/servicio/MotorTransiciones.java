package co.gov.redvital.donacion.dominio.servicio;

import co.gov.redvital.donacion.dominio.excepcion.TransicionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import co.gov.redvital.donacion.dominio.modelo.MatrizTransiciones;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.time.Instant;
import java.util.UUID;

/**
 * Único punto por el que cambia el estado de una unidad. Consulta la matriz
 * cerrada; si el par no está, la transición no ocurre y el estado no cambia.
 * Produce el evento que el historial guarda en la misma transacción.
 */
public final class MotorTransiciones {

    private final MatrizTransiciones matriz;

    public MotorTransiciones(MatrizTransiciones matriz) {
        this.matriz = matriz;
    }

    /** Alta de la unidad: el evento de creación, sin estado anterior. */
    public EventoUnidad crear(Unidad unidad, String actorId, String correlacionId, Instant ahora) {
        if (unidad.estado() != null) {
            throw new TransicionNoPermitida(unidad.estado(), CodigoEstado.CAPTADA);
        }
        EventoUnidad evento = new EventoUnidad(UUID.randomUUID(), unidad.id(), null, CodigoEstado.CAPTADA,
                ActorTipo.USUARIO, actorId, unidad.institucionCustodiaId(), null, correlacionId, ahora);
        unidad.aplicar(evento);
        return evento;
    }

    public EventoUnidad transitar(Unidad unidad, CodigoEstado destino, ActorTipo actorTipo, String actorId,
                                  String observacion, String correlacionId, Instant ahora) {
        CodigoEstado actual = unidad.estado();
        matriz.buscar(actual, destino, actorTipo)
                .orElseThrow(() -> new TransicionNoPermitida(actual, destino));
        EventoUnidad evento = new EventoUnidad(UUID.randomUUID(), unidad.id(), actual, destino, actorTipo,
                actorId, unidad.institucionCustodiaId(), observacion, correlacionId, ahora);
        unidad.aplicar(evento);
        return evento;
    }
}
