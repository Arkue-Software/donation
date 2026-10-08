package co.gov.redvital.donacion.aplicacion.comun;

import co.gov.redvital.donacion.aplicacion.puerto.salida.HuellaPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.IdempotenciaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.ClaveIdempotenciaReutilizada;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * EC-10: una petición reenviada con la misma clave produce la misma respuesta
 * y ningún efecto adicional. La reserva de la clave va en la misma transacción
 * que el hecho; dos reenvíos simultáneos se serializan sobre la clave primaria.
 */
public final class EjecutorIdempotente {

    public static final Duration VIGENCIA = Duration.ofHours(24);

    public record Resultado<T>(T valor, boolean reenvio) {
    }

    private final IdempotenciaPort idempotencia;
    private final HuellaPeticion huellas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public EjecutorIdempotente(IdempotenciaPort idempotencia, HuellaPeticion huellas,
                               UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.idempotencia = idempotencia;
        this.huellas = huellas;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /**
     * @param recursoId   identificador del recurso que creará la primera ejecución
     * @param crear       efecto de la primera ejecución, dentro de la transacción
     * @param reconstruir respuesta de un reenvío, a partir del recurso creado
     */
    public <T> Resultado<T> ejecutar(String sujeto, String clave, String operacion, Object cuerpo, UUID recursoId,
                                     Function<UUID, T> crear, Function<UUID, T> reconstruir) {
        String huella = huellas.calcular(cuerpo);
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            var nueva = new IdempotenciaPort.Operacion(sujeto, clave, operacion, huella, recursoId, 201,
                    ahora, ahora.plus(VIGENCIA));
            if (idempotencia.reservar(nueva)) {
                return new Resultado<>(crear.apply(recursoId), false);
            }
            var previa = idempotencia.buscar(sujeto, clave).orElseThrow(ClaveIdempotenciaReutilizada::new);
            if (!previa.huella().equals(huella) || !previa.operacion().equals(operacion)) {
                throw new ClaveIdempotenciaReutilizada();
            }
            return new Resultado<>(reconstruir.apply(previa.recursoId()), true);
        });
    }

    /** Comprueba una clave sin reservarla, para operaciones que llaman a otro servicio antes de escribir. */
    public java.util.Optional<UUID> reenvioPrevio(String sujeto, String clave, String operacion, Object cuerpo) {
        String huella = huellas.calcular(cuerpo);
        return idempotencia.buscar(sujeto, clave).map(previa -> {
            if (!previa.huella().equals(huella) || !previa.operacion().equals(operacion)) {
                throw new ClaveIdempotenciaReutilizada();
            }
            return previa.recursoId();
        });
    }
}
