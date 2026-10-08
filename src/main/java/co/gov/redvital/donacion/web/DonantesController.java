package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.ActualizarPerfilDonante;
import co.gov.redvital.donacion.aplicacion.casodeuso.BuscarDonantePresente;
import co.gov.redvital.donacion.aplicacion.casodeuso.ConsultasDelDonante;
import co.gov.redvital.donacion.aplicacion.casodeuso.GestionarConsentimientos;
import co.gov.redvital.donacion.aplicacion.casodeuso.RegistrarDonante;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Consentimiento;
import co.gov.redvital.donacion.web.soporte.ClaveIdempotencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M1, gestión de donantes (DD, Tablas 34 y 35). */
@RestController
@RequestMapping("/v1/donantes")
class DonantesController {

    /** El documento y la credencial se reciben pero nunca se devuelven ni se registran. */
    record RegistroPeticion(
            @NotNull @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String documento,
            @NotNull String nombre,
            @NotNull LocalDate fechaNacimiento,
            @NotNull String correoAcceso,
            @NotNull @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String credencial,
            String correo,
            String telefono,
            String municipioRuta,
            boolean autorizaTratamientoDatos,
            boolean autorizaAvisosCampanas,
            @NotNull String versionAviso) {

        @Override
        public String toString() {
            return "RegistroPeticion[datos protegidos]";
        }
    }

    record RegistroRespuesta(UUID id, UUID usuarioId) {
    }

    record PerfilPeticion(String nombre, String correo, String telefono, String municipioRuta) {
    }

    record ConsentimientoPeticion(@NotNull String finalidad, @NotNull Boolean otorgado, @NotNull String versionAviso) {
    }

    record ConsentimientoVista(String finalidad, boolean otorgado, String versionAviso, Instant registradoEn) {

        static ConsentimientoVista de(Consentimiento c) {
            return new ConsentimientoVista(c.finalidad().codigo(), c.otorgado(), c.versionAviso(), c.registradoEn());
        }
    }

    record BusquedaPeticion(@NotNull @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String documento) {

        @Override
        public String toString() {
            return "BusquedaPeticion[datos protegidos]";
        }
    }

    record BusquedaRespuesta(UUID id, String nombre, Vistas.ElegibilidadVista elegibilidad) {
    }

    private final RegistrarDonante registrar;
    private final ConsultasDelDonante consultas;
    private final ActualizarPerfilDonante actualizar;
    private final GestionarConsentimientos consentimientos;
    private final BuscarDonantePresente buscar;

    DonantesController(RegistrarDonante registrar, ConsultasDelDonante consultas, ActualizarPerfilDonante actualizar,
                       GestionarConsentimientos consentimientos, BuscarDonantePresente buscar) {
        this.registrar = registrar;
        this.consultas = consultas;
        this.actualizar = actualizar;
        this.consentimientos = consentimientos;
        this.buscar = buscar;
    }

    @PostMapping
    ResponseEntity<RegistroRespuesta> registrar(@RequestHeader(ClaveIdempotencia.CABECERA) String clave,
                                                @Valid @RequestBody RegistroPeticion p, ContextoPeticion ctx) {
        var resultado = registrar.ejecutar(new RegistrarDonante.Comando(p.documento(), p.nombre(),
                p.fechaNacimiento(), p.correoAcceso(), p.credencial(), p.correo(), p.telefono(), p.municipioRuta(),
                p.autorizaTratamientoDatos(), p.autorizaAvisosCampanas(), p.versionAviso()),
                ClaveIdempotencia.validar(clave), ctx).valor();
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistroRespuesta(resultado.donanteId(),
                resultado.usuarioId()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('donante')")
    ConsultasDelDonante.Perfil perfil(Actor actor) {
        return consultas.perfil(actor);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('donante')")
    ConsultasDelDonante.Perfil actualizar(Actor actor, @RequestBody PerfilPeticion p, ContextoPeticion ctx) {
        return actualizar.ejecutar(actor, new ActualizarPerfilDonante.Comando(p.nombre(), p.correo(), p.telefono(),
                p.municipioRuta()), ctx);
    }

    @GetMapping("/me/elegibilidad")
    @PreAuthorize("hasRole('donante')")
    Vistas.ElegibilidadVista elegibilidad(Actor actor) {
        return Vistas.ElegibilidadVista.de(consultas.elegibilidad(actor));
    }

    @GetMapping("/me/donaciones")
    @PreAuthorize("hasRole('donante')")
    List<ConsultasDelDonante.DonacionPropia> donaciones(Actor actor) {
        return consultas.donaciones(actor);
    }

    @GetMapping("/me/reconocimientos")
    @PreAuthorize("hasRole('donante')")
    List<ConsultasDelDonante.ReconocimientoPropio> reconocimientos(Actor actor) {
        return consultas.reconocimientos(actor);
    }

    @GetMapping("/me/consentimientos")
    @PreAuthorize("hasRole('donante')")
    List<ConsentimientoVista> consentimientos(Actor actor) {
        return consentimientos.vigentes(actor).stream().map(ConsentimientoVista::de).toList();
    }

    @PostMapping("/me/consentimientos")
    @PreAuthorize("hasRole('donante')")
    List<ConsentimientoVista> registrarConsentimiento(Actor actor, @Valid @RequestBody ConsentimientoPeticion p,
                                                      ContextoPeticion ctx) {
        return consentimientos.registrar(actor, p.finalidad(), p.otorgado(), p.versionAviso(), ctx).stream()
                .map(ConsentimientoVista::de).toList();
    }

    @PostMapping("/busqueda")
    @PreAuthorize("hasRole('operador')")
    BusquedaRespuesta buscar(Actor actor, @Valid @RequestBody BusquedaPeticion p, ContextoPeticion ctx) {
        var r = buscar.ejecutar(actor, p.documento(), ctx);
        return new BusquedaRespuesta(r.id(), r.nombre(), Vistas.ElegibilidadVista.de(r.elegibilidad()));
    }
}
