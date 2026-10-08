package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultarInventario;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarAlertas;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarUmbrales;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M4, inventario, umbrales y alertas de la institución propia (DD, Tabla 36). */
@RestController
class InventarioController {

    record UmbralPeticion(@NotNull String componente, String grupoSanguineo, int minimoUnidades,
                          int diasPreviosVencimiento) {
    }

    record UmbralesPeticion(@NotNull List<@Valid UmbralPeticion> umbrales) {
    }

    private final ConsultarInventario inventario;
    private final GestionarUmbrales umbrales;
    private final GestionarAlertas alertas;

    InventarioController(ConsultarInventario inventario, GestionarUmbrales umbrales, GestionarAlertas alertas) {
        this.inventario = inventario;
        this.umbrales = umbrales;
        this.alertas = alertas;
    }

    @GetMapping("/v1/inventario")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    List<Vistas.ExistenciaVista> existencias(Actor actor,
                                             @RequestParam(name = "institucion_id", required = false) UUID institucion,
                                             @RequestParam(required = false) String componente,
                                             @RequestParam(name = "grupo_sanguineo", required = false) String grupo,
                                             ContextoPeticion ctx) {
        return inventario.ejecutar(actor, institucion, componente, grupo, ctx).stream()
                .map(Vistas.ExistenciaVista::de).toList();
    }

    @GetMapping("/v1/inventario/umbrales")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    List<Vistas.UmbralVista> umbrales(Actor actor) {
        return umbrales.listar(actor).stream().map(Vistas.UmbralVista::de).toList();
    }

    @PutMapping("/v1/inventario/umbrales")
    @PreAuthorize("hasRole('admin_banco')")
    List<Vistas.UmbralVista> configurarUmbrales(Actor actor, @Valid @RequestBody UmbralesPeticion p,
                                                ContextoPeticion ctx) {
        var comandos = p.umbrales().stream()
                .map(u -> new GestionarUmbrales.Comando(u.componente(), u.grupoSanguineo(), u.minimoUnidades(),
                        u.diasPreviosVencimiento()))
                .toList();
        return umbrales.guardar(actor, comandos, ctx).stream().map(Vistas.UmbralVista::de).toList();
    }

    @GetMapping("/v1/alertas")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    List<Vistas.AlertaVista> alertas(Actor actor, @RequestParam(required = false) String estado) {
        return alertas.listar(actor, estado).stream().map(Vistas.AlertaVista::de).toList();
    }

    @PostMapping("/v1/alertas/{id}/atencion")
    @PreAuthorize("hasRole('admin_banco')")
    Vistas.AlertaVista atender(Actor actor, @PathVariable UUID id, ContextoPeticion ctx) {
        return Vistas.AlertaVista.de(alertas.atender(actor, id, ctx));
    }
}
