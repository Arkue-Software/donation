package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ConfirmarFraccionamiento;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDeDonaciones;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarDonacion;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.web.soporte.ClaveIdempotencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M3, registro de donaciones (DD, Tabla 35). La institución sale siempre del token. */
@RestController
@RequestMapping("/v1/donaciones")
class DonacionesController {

    record ComponentePeticion(@NotNull String componente, Integer volumenMl) {
    }

    record RegistroPeticion(UUID donanteId, String intencionCodigo, String tipoDonacion,
                            @NotNull String grupoSanguineo, UUID campaniaId,
                            @NotNull List<@Valid ComponentePeticion> componentes) {
    }

    record FraccionamientoPeticion(String observacionCodigo) {
    }

    private final RegistrarDonacion registrar;
    private final ConfirmarFraccionamiento fraccionar;
    private final ConsultasDeDonaciones consultas;

    DonacionesController(RegistrarDonacion registrar, ConfirmarFraccionamiento fraccionar,
                         ConsultasDeDonaciones consultas) {
        this.registrar = registrar;
        this.fraccionar = fraccionar;
        this.consultas = consultas;
    }

    @PostMapping
    @PreAuthorize("hasRole('operador')")
    ResponseEntity<Vistas.DonacionVista> registrar(Actor actor, @RequestHeader(ClaveIdempotencia.CABECERA) String clave,
                                                   @Valid @RequestBody RegistroPeticion p, ContextoPeticion ctx) {
        var comando = new RegistrarDonacion.Comando(p.donanteId(), p.intencionCodigo(), p.tipoDonacion(),
                p.grupoSanguineo(), p.campaniaId(), p.componentes().stream()
                .map(c -> new RegistrarDonacion.ComponenteSolicitado(c.componente(), c.volumenMl())).toList());
        var resultado = registrar.ejecutar(actor, comando, ClaveIdempotencia.validar(clave), ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(Vistas.DonacionVista.de(resultado.valor()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operador', 'admin_banco')")
    Vistas.DonacionVista consultar(Actor actor, @PathVariable UUID id) {
        return Vistas.DonacionVista.de(consultas.consultar(actor, id));
    }

    @PostMapping("/{id}/fraccionamiento")
    @PreAuthorize("hasRole('operador')")
    Vistas.DonacionVista fraccionar(Actor actor, @PathVariable UUID id,
                                    @RequestHeader(ClaveIdempotencia.CABECERA) String clave,
                                    @RequestBody(required = false) FraccionamientoPeticion p, ContextoPeticion ctx) {
        var comando = new ConfirmarFraccionamiento.Comando(p == null ? null : p.observacionCodigo());
        return Vistas.DonacionVista.de(fraccionar.ejecutar(actor, id, comando, ClaveIdempotencia.validar(clave), ctx)
                .valor());
    }
}
