package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioReconocimientos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Elegibilidad;
import co.gov.redvital.donacion.dominio.servicio.PoliticaElegibilidad;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecturas del propio donante (U2), resueltas siempre por la reivindicación
 * sub: no existe forma de pedir el perfil de otra persona.
 */
public final class ConsultasDelDonante {

    /** Mensaje neutro, idéntico para todo donante, cualquiera que sea el destino de sus unidades (D-01). */
    public static final String MENSAJE_DONACION =
            "Gracias por donar. Tu donación ayuda a sostener la reserva de sangre del país.";

    public record Perfil(String nombre, String correo, String telefono, String municipioRuta,
                         String grupoSanguineo, LocalDate fechaUltimaDonacion, int totalDonaciones) {
    }

    public record DonacionPropia(UUID id, Instant fechaCaptacion, UUID institucionId, UUID campaniaId,
                                 String mensaje) {
    }

    public record ReconocimientoPropio(String codigo, String nombre, String descripcion,
                                       int criterioDonaciones, int orden, Instant otorgadoEn) {
    }

    private final RepositorioDonantes donantes;
    private final RepositorioDonaciones donaciones;
    private final RepositorioReconocimientos reconocimientos;
    private final CatalogoPort catalogo;
    private final PoliticaElegibilidad politica;
    private final Clock reloj;

    public ConsultasDelDonante(RepositorioDonantes donantes, RepositorioDonaciones donaciones,
                               RepositorioReconocimientos reconocimientos, CatalogoPort catalogo,
                               PoliticaElegibilidad politica, Clock reloj) {
        this.donantes = donantes;
        this.donaciones = donaciones;
        this.reconocimientos = reconocimientos;
        this.catalogo = catalogo;
        this.politica = politica;
        this.reloj = reloj;
    }

    public Perfil perfil(Actor actor) {
        Donante d = donante(actor);
        return new Perfil(d.nombre(), d.correo(), d.telefono(), d.municipioRuta(), d.grupoSanguineo(),
                d.fechaUltimaDonacion(), donaciones.contarDeDonante(d.id()));
    }

    /** El cálculo vive solo aquí; la web no lo replica (EC-21). */
    public Elegibilidad elegibilidad(Actor actor) {
        Donante d = donante(actor);
        return politica.evaluar(d.fechaNacimiento(), d.fechaUltimaDonacion(), LocalDate.now(reloj));
    }

    public List<DonacionPropia> donaciones(Actor actor) {
        Donante d = donante(actor);
        return donaciones.deDonante(d.id()).stream()
                .sorted(Comparator.comparing(co.gov.redvital.donacion.dominio.modelo.Donacion::fechaCaptacion).reversed())
                .map(x -> new DonacionPropia(x.id(), x.fechaCaptacion(), x.institucionId(), x.campaniaId(), MENSAJE_DONACION))
                .toList();
    }

    public List<ReconocimientoPropio> reconocimientos(Actor actor) {
        Donante d = donante(actor);
        Map<String, Instant> otorgados = reconocimientos.otorgados(d.id());
        return catalogo.reconocimientos().stream()
                .sorted(Comparator.comparingInt(co.gov.redvital.donacion.dominio.modelo.Reconocimiento::orden))
                .map(r -> new ReconocimientoPropio(r.codigo(), r.nombre(), r.descripcion(), r.criterioDonaciones(),
                        r.orden(), otorgados.get(r.codigo())))
                .toList();
    }

    Donante donante(Actor actor) {
        return donantes.porUsuario(actor.cuentaId())
                .filter(Donante::activo)
                .orElseThrow(() -> new NoEncontrado("No encontramos tu perfil de donante."));
    }
}
