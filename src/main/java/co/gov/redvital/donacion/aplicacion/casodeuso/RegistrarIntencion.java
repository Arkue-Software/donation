package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioIntenciones;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.CodigoIntencion;
import co.gov.redvital.donacion.dominio.modelo.EstadoIntencion;
import co.gov.redvital.donacion.dominio.modelo.IntencionDonacion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * RF-01, modalidad anónima. Cero campos de identificación (EC-17): solo un
 * grupo autodeclarado y un municipio, ambos opcionales. La intención caduca
 * sola a los treinta días (D-08).
 */
public final class RegistrarIntencion {

    public static final Duration VIGENCIA = Duration.ofDays(30);
    private static final String OPERACION = "registrar_intencion";

    public record Comando(String grupoSanguineo, String municipioRuta) {
    }

    public record Resultado(String codigo, Instant expiraEn) {
    }

    private final RepositorioIntenciones intenciones;
    private final CatalogoPort catalogo;
    private final EjecutorIdempotente idempotente;
    private final Auditor auditor;
    private final RandomGenerator azar;
    private final Clock reloj;

    public RegistrarIntencion(RepositorioIntenciones intenciones, CatalogoPort catalogo,
                              EjecutorIdempotente idempotente, Auditor auditor, RandomGenerator azar, Clock reloj) {
        this.intenciones = intenciones;
        this.catalogo = catalogo;
        this.idempotente = idempotente;
        this.auditor = auditor;
        this.azar = azar;
        this.reloj = reloj;
    }

    public EjecutorIdempotente.Resultado<Resultado> ejecutar(Comando comando, String claveIdempotencia,
                                                             ContextoPeticion ctx) {
        if (comando.grupoSanguineo() != null && !catalogo.existeGrupoSanguineo(comando.grupoSanguineo())) {
            throw new DatosInvalidos("El campo grupo_sanguineo no es un grupo válido.");
        }
        Validaciones.rutaMunicipio(comando.municipioRuta(), "municipio_ruta");

        return idempotente.ejecutar(ctx.sujetoAnonimo(), claveIdempotencia, OPERACION, comando, UUID.randomUUID(),
                id -> {
                    Instant ahora = reloj.instant();
                    var intencion = new IntencionDonacion(id, CodigoIntencion.generar(azar), comando.grupoSanguineo(),
                            comando.municipioRuta(), EstadoIntencion.PENDIENTE, ahora, ahora.plus(VIGENCIA), null);
                    intenciones.insertar(intencion);
                    auditor.permitidoAnonimo(OPERACION, "intencion_donacion", id, ctx);
                    return new Resultado(intencion.codigo(), intencion.expiraEn());
                },
                id -> intenciones.buscar(id)
                        .map(i -> new Resultado(i.codigo(), i.expiraEn()))
                        .orElseThrow(NoEncontrado::new));
    }
}
