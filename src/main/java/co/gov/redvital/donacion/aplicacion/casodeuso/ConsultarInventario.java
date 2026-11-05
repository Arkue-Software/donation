package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.ResolutorAlcance;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ConsultaInventarioPort;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.FueraDeJurisdiccion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Alcance;
import co.gov.redvital.donacion.dominio.modelo.Existencia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RF-07: existencias de las instituciones que el usuario puede leer: la suya, o
 * las de su territorio para U5 y U6 (DD 15.5, grupo 7). Pedir una institución
 * fuera de ese alcance es una consulta fuera de jurisdicción: 403 que nombra
 * solo el ámbito propio, y el intento queda auditado (DD, Tabla 24).
 */
public final class ConsultarInventario {

    private final ConsultaInventarioPort inventario;
    private final CatalogoPort catalogo;
    private final Auditor auditor;
    private final ZoneId zona;
    private final Clock reloj;
    private final ResolutorAlcance alcance;

    public ConsultarInventario(ConsultaInventarioPort inventario, CatalogoPort catalogo, Auditor auditor,
                               ZoneId zona, Clock reloj, ResolutorAlcance alcance) {
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.auditor = auditor;
        this.zona = zona;
        this.reloj = reloj;
        this.alcance = alcance;
    }

    public List<Existencia> ejecutar(Actor actor, UUID institucionSolicitada, String componente, String grupo,
                                     ContextoPeticion ctx) {
        Alcance propio = alcance.de(actor);
        if (institucionSolicitada != null && !propio.incluye(institucionSolicitada)) {
            auditor.denegado(actor, "consultar_inventario", "inventario", null,
                    "institucion:" + institucionSolicitada, ctx);
            throw new FueraDeJurisdiccion();
        }
        if (componente != null && catalogo.componente(componente).isEmpty()) {
            throw new DatosInvalidos("El parámetro componente no es un componente válido.");
        }
        if (grupo != null && !catalogo.existeGrupoSanguineo(grupo)) {
            throw new DatosInvalidos("El parámetro grupo_sanguineo no es un grupo válido.");
        }
        Set<UUID> instituciones = institucionSolicitada != null ? Set.of(institucionSolicitada) : propio.instituciones();
        return inventario.existencias(instituciones, componente, grupo, LocalDate.now(reloj.withZone(zona)));
    }
}
