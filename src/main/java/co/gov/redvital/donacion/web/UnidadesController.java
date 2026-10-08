package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDeUnidades;
import co.gov.redvital.donacion.aplicacion.casodeuso.OperarUnidad;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.Pagina;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.web.soporte.ClaveIdempotencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M3, ciclo de vida de la unidad (DD, Tabla 35). Solo el operador del banco
 * custodio escribe; el administrador de banco consulta.
 */
@RestController
@RequestMapping("/v1/unidades")
class UnidadesController {

    record ObservacionPeticion(String observacionCodigo) {
    }

    /** Un único campo. El esquema rechaza cualquier propiedad adicional: el contrato es la barrera (EC-02). */
    record TamizajePeticion(@NotNull Boolean apta) {
    }

    record ConfirmacionPeticion(@NotNull String confirmacion, String observacionCodigo) {
    }

    private final ConsultasDeUnidades consultas;
    private final OperarUnidad operar;

    UnidadesController(ConsultasDeUnidades consultas, OperarUnidad operar) {
        this.consultas = consultas;
        this.operar = operar;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    Pagina<Vistas.UnidadVista> listar(Actor actor, @RequestParam(required = false) String estado,
                                      @RequestParam(required = false) String componente,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "25") int tamano) {
        return consultas.listar(actor, estado, componente, pagina, tamano).map(Vistas.UnidadVista::de);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    Vistas.UnidadVista detalle(Actor actor, @PathVariable UUID id) {
        return Vistas.UnidadVista.de(consultas.detalle(actor, id));
    }

    @GetMapping("/{id}/eventos")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    List<Vistas.EventoVista> eventos(Actor actor, @PathVariable UUID id) {
        return consultas.eventos(actor, id).stream().map(Vistas.EventoVista::de).toList();
    }

    @PostMapping("/{id}/ingreso-tamizaje")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista ingresoTamizaje(Actor actor, @PathVariable UUID id,
                                           @RequestBody(required = false) ObservacionPeticion p, ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.iniciarTamizaje(actor, id, observacion(p), ctx));
    }

    @PostMapping("/{id}/tamizaje")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista tamizaje(Actor actor, @PathVariable UUID id, @Valid @RequestBody TamizajePeticion p,
                                    ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.registrarTamizaje(actor, id, p.apta(), ctx));
    }

    @PostMapping("/{id}/reserva")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista reservar(Actor actor, @PathVariable UUID id,
                                    @RequestBody(required = false) ObservacionPeticion p, ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.reservar(actor, id, observacion(p), ctx));
    }

    @DeleteMapping("/{id}/reserva")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista liberar(Actor actor, @PathVariable UUID id, ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.liberarReserva(actor, id, null, ctx));
    }

    @PostMapping("/{id}/despacho")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista despachar(Actor actor, @PathVariable UUID id,
                                     @RequestHeader(ClaveIdempotencia.CABECERA) String clave,
                                     @Valid @RequestBody ConfirmacionPeticion p, ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.despachar(actor, id, p.confirmacion(), p.observacionCodigo(),
                ClaveIdempotencia.validar(clave), ctx).valor());
    }

    @PostMapping("/{id}/disposicion-final")
    @PreAuthorize("hasRole('operador')")
    Vistas.TransicionVista disponer(Actor actor, @PathVariable UUID id,
                                    @RequestHeader(ClaveIdempotencia.CABECERA) String clave,
                                    @Valid @RequestBody ConfirmacionPeticion p, ContextoPeticion ctx) {
        return Vistas.TransicionVista.de(operar.disponer(actor, id, p.confirmacion(), p.observacionCodigo(),
                ClaveIdempotencia.validar(clave), ctx).valor());
    }

    private static String observacion(ObservacionPeticion p) {
        return p == null ? null : p.observacionCodigo();
    }
}
