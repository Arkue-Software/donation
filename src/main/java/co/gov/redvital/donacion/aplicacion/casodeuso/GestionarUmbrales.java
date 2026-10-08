package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUmbrales;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Umbral;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** RF-19 y RF-20: umbrales de escasez y de vencimiento próximo, solo de la institución propia. */
public final class GestionarUmbrales {

    public record Comando(String componente, String grupoSanguineo, int minimoUnidades, int diasPreviosVencimiento) {
    }

    private final RepositorioUmbrales umbrales;
    private final CatalogoPort catalogo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public GestionarUmbrales(RepositorioUmbrales umbrales, CatalogoPort catalogo, UnidadDeTrabajo unidadDeTrabajo,
                             Auditor auditor, Clock reloj) {
        this.umbrales = umbrales;
        this.catalogo = catalogo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public List<Umbral> listar(Actor actor) {
        return umbrales.deInstitucion(actor.institucion());
    }

    public List<Umbral> guardar(Actor actor, List<Comando> comandos, ContextoPeticion ctx) {
        var institucion = actor.institucion();
        if (comandos == null || comandos.isEmpty() || comandos.size() > 50) {
            throw new DatosInvalidos("Envía entre 1 y 50 umbrales.");
        }
        var vistos = new HashSet<String>();
        for (Comando c : comandos) {
            if (catalogo.componente(c.componente()).isEmpty()) {
                throw new DatosInvalidos("El campo componente no es un componente válido.");
            }
            if (c.grupoSanguineo() != null && !catalogo.existeGrupoSanguineo(c.grupoSanguineo())) {
                throw new DatosInvalidos("El campo grupo_sanguineo no es un grupo válido.");
            }
            if (c.minimoUnidades() <= 0 || c.minimoUnidades() > 10_000
                    || c.diasPreviosVencimiento() <= 0 || c.diasPreviosVencimiento() > 365) {
                throw new DatosInvalidos("Los umbrales deben ser números positivos dentro de rango.");
            }
            if (!vistos.add(c.componente() + "|" + c.grupoSanguineo())) {
                throw new DatosInvalidos("Cada combinación de componente y grupo se envía una sola vez.");
            }
        }
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            var usuario = actor.cuentaId();
            for (Comando c : comandos) {
                umbrales.guardar(new Umbral(institucion, c.componente(), c.grupoSanguineo(), c.minimoUnidades(),
                        c.diasPreviosVencimiento(), usuario, ahora));
            }
            auditor.permitido(actor, "configurar_umbrales", "umbral_inventario", null, ctx);
            return umbrales.deInstitucion(institucion);
        });
    }
}
