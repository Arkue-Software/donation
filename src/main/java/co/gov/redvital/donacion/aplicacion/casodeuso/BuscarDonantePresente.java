package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ResumidorDocumento;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Elegibilidad;
import co.gov.redvital.donacion.dominio.servicio.PoliticaElegibilidad;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M1-U3-01: el operador identifica al donante presente por su documento y
 * recibe lo mínimo para identificarlo. Nunca la historia clínica, nunca el
 * motivo de una no elegibilidad: solo la fecha.
 */
public final class BuscarDonantePresente {

    public record Resultado(UUID id, String nombre, Elegibilidad elegibilidad) {
    }

    private final RepositorioDonantes donantes;
    private final ResumidorDocumento resumidor;
    private final PoliticaElegibilidad politica;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public BuscarDonantePresente(RepositorioDonantes donantes, ResumidorDocumento resumidor,
                                 PoliticaElegibilidad politica, UnidadDeTrabajo unidadDeTrabajo,
                                 Auditor auditor, Clock reloj) {
        this.donantes = donantes;
        this.resumidor = resumidor;
        this.politica = politica;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public Resultado ejecutar(Actor actor, String documento, ContextoPeticion ctx) {
        if (documento == null || documento.isBlank()) {
            throw new DatosInvalidos("El campo documento es obligatorio.");
        }
        String hash = resumidor.resumir(documento.trim());
        return unidadDeTrabajo.ejecutar(() -> {
            Donante d = donantes.porDocumentoHash(hash)
                    .filter(Donante::activo)
                    .orElseThrow(() -> new NoEncontrado("No hay un donante registrado con ese documento."));
            auditor.permitido(actor, "buscar_donante", "donante", d.id(), ctx);
            return new Resultado(d.id(), d.nombre(),
                    politica.evaluar(d.fechaNacimiento(), d.fechaUltimaDonacion(), LocalDate.now(reloj)));
        });
    }
}
