package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioIntenciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.CodigoIntencion;
import co.gov.redvital.donacion.dominio.modelo.EstadoIntencion;
import co.gov.redvital.donacion.dominio.modelo.Rol;
import java.time.Clock;
import java.time.Instant;

/**
 * Consulta con el código (M1-U1-06). Responde por la intención, nunca por la
 * donación: no revela si la donación llegó a producirse ni nada de sus
 * unidades. El operador ve además el grupo autodeclarado y el municipio, que
 * necesita para registrar la donación.
 */
public final class ConsultarIntencion {

    public record Vista(String codigo, String estado, Instant expiraEn, String grupoSanguineo, String municipioRuta) {
    }

    private final RepositorioIntenciones intenciones;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public ConsultarIntencion(RepositorioIntenciones intenciones, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.intenciones = intenciones;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /** @param actor nulo cuando la consulta es anónima */
    public Vista ejecutar(String codigoIngresado, Actor actor) {
        String codigo = CodigoIntencion.normalizar(codigoIngresado).orElseThrow(this::noEncontrada);
        return unidadDeTrabajo.ejecutar(() -> {
            var intencion = intenciones.porCodigo(codigo).orElseThrow(this::noEncontrada);
            EstadoIntencion efectivo = intencion.estadoEn(reloj.instant());
            if (efectivo != intencion.estado()) {
                intenciones.marcarCaducada(intencion.id());
            }
            boolean operador = actor != null && actor.rol() == Rol.OPERADOR;
            return new Vista(intencion.codigo(), efectivo.codigo(), intencion.expiraEn(),
                    operador ? intencion.grupoSanguineo() : null,
                    operador ? intencion.municipioRuta() : null);
        });
    }

    private NoEncontrado noEncontrada() {
        return new NoEncontrado("No encontramos un registro con ese código.");
    }
}
