package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ConsultaInventarioPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioAlertas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUmbrales;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.modelo.Alerta;
import co.gov.redvital.donacion.dominio.modelo.EstadoAlerta;
import co.gov.redvital.donacion.dominio.modelo.TipoAlerta;
import co.gov.redvital.donacion.dominio.modelo.Umbral;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Proceso programado de RF-19 y RF-20. Abre una alerta de escasez cuando las
 * existencias bajan del umbral y la cierra como caducada cuando lo superan de
 * nuevo; abre una alerta de vencimiento próximo por unidad disponible que entra
 * en la ventana del umbral, y la cierra cuando la unidad deja de estarlo.
 */
public final class EvaluarAlertas {

    public static final long CLAVE_BLOQUEO = 0x52564141L; // "RVAA"

    private final RepositorioUmbrales umbrales;
    private final RepositorioAlertas alertas;
    private final RepositorioUnidades unidades;
    private final ConsultaInventarioPort inventario;
    private final BloqueoConsultivoPort bloqueo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final ZoneId zona;
    private final Clock reloj;

    public EvaluarAlertas(RepositorioUmbrales umbrales, RepositorioAlertas alertas, RepositorioUnidades unidades,
                          ConsultaInventarioPort inventario, BloqueoConsultivoPort bloqueo,
                          UnidadDeTrabajo unidadDeTrabajo, ZoneId zona, Clock reloj) {
        this.umbrales = umbrales;
        this.alertas = alertas;
        this.unidades = unidades;
        this.inventario = inventario;
        this.bloqueo = bloqueo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.zona = zona;
        this.reloj = reloj;
    }

    public void ejecutar() {
        unidadDeTrabajo.ejecutar(() -> {
            if (!bloqueo.intentar(CLAVE_BLOQUEO)) {
                return;
            }
            Instant ahora = reloj.instant();
            LocalDate hoy = ahora.atZone(zona).toLocalDate();
            List<Umbral> todos = umbrales.todos();
            evaluarEscasez(todos, ahora, hoy);
            evaluarVencimientos(todos, ahora, hoy);
        });
    }

    private void evaluarEscasez(List<Umbral> todos, Instant ahora, LocalDate hoy) {
        List<Alerta> abiertas = alertas.abiertas(TipoAlerta.ESCASEZ);
        for (Umbral u : todos) {
            long disponibles = inventario.disponibles(u.institucionId(), u.componente(), u.grupoSanguineo(), hoy);
            Optional<Alerta> abierta = abiertas.stream()
                    .filter(a -> a.institucionId().equals(u.institucionId())
                            && a.componente().equals(u.componente())
                            && Objects.equals(a.grupoSanguineo(), u.grupoSanguineo()))
                    .findFirst();
            if (disponibles < u.minimoUnidades()) {
                alertas.abrirOActualizar(new Alerta(UUID.randomUUID(), TipoAlerta.ESCASEZ, u.institucionId(),
                        u.componente(), u.grupoSanguineo(), null, (int) disponibles, EstadoAlerta.ABIERTA,
                        ahora, null, null));
            } else {
                abierta.ifPresent(a -> alertas.cerrar(a.id(), EstadoAlerta.CADUCADA, ahora, null));
            }
        }
    }

    private void evaluarVencimientos(List<Umbral> todos, Instant ahora, LocalDate hoy) {
        Set<UUID> enVentana = new HashSet<>();
        Set<UUID> instituciones = new HashSet<>();
        todos.forEach(u -> instituciones.add(u.institucionId()));
        for (UUID institucion : instituciones) {
            for (Unidad unidad : unidades.disponiblesDe(institucion, hoy)) {
                Optional<Umbral> aplicable = umbralAplicable(todos, unidad);
                if (aplicable.isEmpty()) {
                    continue;
                }
                long dias = ChronoUnit.DAYS.between(hoy, unidad.fechaVencimiento());
                if (dias <= aplicable.get().diasPreviosVencimiento()) {
                    enVentana.add(unidad.id());
                    alertas.abrirOActualizar(new Alerta(UUID.randomUUID(), TipoAlerta.VENCIMIENTO_PROXIMO, institucion,
                            unidad.componente(), unidad.grupoSanguineo(), unidad.id(), (int) dias,
                            EstadoAlerta.ABIERTA, ahora, null, null));
                }
            }
        }
        for (Alerta a : alertas.abiertas(TipoAlerta.VENCIMIENTO_PROXIMO)) {
            if (!enVentana.contains(a.unidadId())) {
                alertas.cerrar(a.id(), EstadoAlerta.CADUCADA, ahora, null);
            }
        }
    }

    /** El umbral específico del grupo gana sobre el general. */
    private Optional<Umbral> umbralAplicable(List<Umbral> todos, Unidad unidad) {
        return todos.stream()
                .filter(u -> u.institucionId().equals(unidad.institucionCustodiaId())
                        && u.componente().equals(unidad.componente())
                        && (u.grupoSanguineo() == null || u.grupoSanguineo().equals(unidad.grupoSanguineo())))
                .min((a, b) -> Boolean.compare(a.grupoSanguineo() == null, b.grupoSanguineo() == null));
    }
}
