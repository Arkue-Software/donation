package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import java.time.Clock;
import java.util.regex.Pattern;

/** La edición admite nombre, contacto y municipio; nunca el grupo sanguíneo ni el documento. */
public final class ActualizarPerfilDonante {

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+ ]{7,20}$");

    public record Comando(String nombre, String correo, String telefono, String municipioRuta) {
    }

    private final ConsultasDelDonante consultas;
    private final RepositorioDonantes donantes;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public ActualizarPerfilDonante(ConsultasDelDonante consultas, RepositorioDonantes donantes,
                                   UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        this.consultas = consultas;
        this.donantes = donantes;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public ConsultasDelDonante.Perfil ejecutar(Actor actor, Comando c, ContextoPeticion ctx) {
        String nombre = Validaciones.requerido(c.nombre(), "nombre", 120);
        String correo = Validaciones.opcional(c.correo(), "correo", 160);
        if (correo != null && !CORREO.matcher(correo).matches()) {
            throw new DatosInvalidos("El campo correo no es un correo válido.");
        }
        String telefono = Validaciones.opcional(c.telefono(), "telefono", 20);
        if (telefono != null && !TELEFONO.matcher(telefono).matches()) {
            throw new DatosInvalidos("El campo telefono no tiene un formato válido.");
        }
        Validaciones.rutaMunicipio(c.municipioRuta(), "municipio_ruta");
        unidadDeTrabajo.ejecutar(() -> {
            Donante d = consultas.donante(actor);
            donantes.actualizarContacto(d.id(), nombre, correo, telefono, c.municipioRuta(), reloj.instant());
            auditor.permitido(actor, "actualizar_perfil_donante", "donante", d.id(), ctx);
        });
        return consultas.perfil(actor);
    }
}
