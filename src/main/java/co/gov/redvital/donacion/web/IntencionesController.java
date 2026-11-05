package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultarIntencion;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarIntencion;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.web.soporte.ClaveIdempotencia;
import co.gov.redvital.donacion.web.soporte.Opcional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M1, registro anónimo (DD, Tabla 34). Ambas operaciones son públicas. */
@RestController
@RequestMapping("/v1/intenciones")
class IntencionesController {

    record RegistroPeticion(String grupoSanguineo, String municipioRuta) {
    }

    private final RegistrarIntencion registrar;
    private final ConsultarIntencion consultar;

    IntencionesController(RegistrarIntencion registrar, ConsultarIntencion consultar) {
        this.registrar = registrar;
        this.consultar = consultar;
    }

    @PostMapping
    ResponseEntity<RegistrarIntencion.Resultado> registrar(
            @RequestHeader(ClaveIdempotencia.CABECERA) String clave,
            @RequestBody(required = false) RegistroPeticion cuerpo, ContextoPeticion ctx) {
        var peticion = cuerpo == null ? new RegistroPeticion(null, null) : cuerpo;
        var resultado = registrar.ejecutar(new RegistrarIntencion.Comando(peticion.grupoSanguineo(),
                peticion.municipioRuta()), ClaveIdempotencia.validar(clave), ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado.valor());
    }

    @GetMapping("/{codigo}")
    ConsultarIntencion.Vista consultar(@PathVariable String codigo, @Opcional Actor actor) {
        return consultar.ejecutar(codigo, actor);
    }
}
