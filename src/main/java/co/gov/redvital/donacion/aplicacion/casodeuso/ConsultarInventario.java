package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ConsultaInventarioPort;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.FueraDeJurisdiccion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Existencia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * RF-07: existencias de la institución del usuario. Pedir explícitamente otra
 * institución es una consulta fuera de jurisdicción: 403 que nombra solo el
 * ámbito propio, y el intento queda auditado (DD, Tabla 24).
 */
public final class ConsultarInventario {

    private final ConsultaInventarioPort inventario;
    private final CatalogoPort catalogo;
    private final Auditor auditor;
    private final ZoneId zona;
    private final Clock reloj;

    public ConsultarInventario(ConsultaInventarioPort inventario, CatalogoPort catalogo, Auditor auditor,
                               ZoneId zona, Clock reloj) {
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.auditor = auditor;
        this.zona = zona;
        this.reloj = reloj;
    }

    public List<Existencia> ejecutar(Actor actor, UUID institucionSolicitada, String componente, String grupo,
                                     ContextoPeticion ctx) {
        UUID propia = actor.institucion();
        if (institucionSolicitada != null && !institucionSolicitada.equals(propia)) {
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
        return inventario.existencias(propia, componente, grupo, LocalDate.now(reloj.withZone(zona)));
    }
}
