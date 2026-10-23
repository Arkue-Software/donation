package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultarContactoDonante;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Rol;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operación interna para el trabajador de Notificaciones (DD, Sección 13). No
 * tiene ruta en el gateway. Solo el token de servicio con sub
 * {@code notificaciones} la usa; cualquier otro sujeto recibe 404, como ante
 * una ruta inexistente.
 */
@RestController
class ContactoInternoController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ContactoVista(boolean contactable, String correo, String nombre) {
    }

    private static final String NOTIFICACIONES = "notificaciones";

    private final ConsultarContactoDonante contacto;

    ContactoInternoController(ConsultarContactoDonante contacto) {
        this.contacto = contacto;
    }

    @GetMapping("/internal/v1/donantes/{id}/contacto")
    ContactoVista contacto(Actor actor, @PathVariable UUID id, @RequestParam("tipo") String tipo) {
        if (actor.rol() != Rol.SERVICIO || !NOTIFICACIONES.equals(actor.sub())) {
            throw new NoEncontrado();
        }
        var c = contacto.ejecutar(id, tipo);
        return new ContactoVista(c.contactable(), c.correo(), c.nombre());
    }
}
