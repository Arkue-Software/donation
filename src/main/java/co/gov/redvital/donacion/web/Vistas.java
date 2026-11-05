package co.gov.redvital.donacion.web;

import co.gov.redvital.donacion.aplicacion.casodeuso.DonacionRegistrada;
import co.gov.redvital.donacion.aplicacion.casodeuso.OperarUnidad;
import co.gov.redvital.donacion.dominio.modelo.Alerta;
import co.gov.redvital.donacion.dominio.modelo.Elegibilidad;
import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import co.gov.redvital.donacion.dominio.modelo.Existencia;
import co.gov.redvital.donacion.dominio.modelo.Umbral;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Formas de respuesta del contrato. Los nombres se serializan en snake_case;
 * ninguna forma tiene un campo para la causa de una unidad no apta (EC-02).
 */
final class Vistas {

    private Vistas() {
    }

    record UnidadVista(UUID id, UUID donacionId, String componente, String grupoSanguineo,
                       UUID institucionCustodiaId, String estado, Boolean apta, LocalDate fechaVencimiento,
                       Integer volumenMl, Instant fechaCaptacion) {

        static UnidadVista de(Unidad u) {
            return new UnidadVista(u.id(), u.donacionId(), u.componente(), u.grupoSanguineo(),
                    u.institucionCustodiaId(), u.estado().codigo(), u.apta(), u.fechaVencimiento(), u.volumenMl(),
                    u.fechaCaptacion());
        }
    }

    record EventoVista(UUID id, String estadoAnterior, String estadoNuevo, String actorTipo, String actorId,
                       UUID institucionId, String observacion, Instant ocurridoEn) {

        static EventoVista de(EventoUnidad e) {
            return new EventoVista(e.id(), e.estadoAnterior() == null ? null : e.estadoAnterior().codigo(),
                    e.estadoNuevo().codigo(), e.actorTipo().codigo(), e.actorId(), e.institucionId(), e.observacion(),
                    e.ocurridoEn());
        }
    }

    record TransicionVista(UUID unidadId, UUID eventoId, String estadoAnterior, String estadoNuevo,
                           Instant ocurridoEn) {

        static TransicionVista de(OperarUnidad.Resultado r) {
            return new TransicionVista(r.unidadId(), r.eventoId(),
                    r.estadoAnterior() == null ? null : r.estadoAnterior().codigo(), r.estadoNuevo().codigo(),
                    r.ocurridoEn());
        }
    }

    record DonacionVista(UUID id, UUID donanteId, UUID intencionId, String tipoDonacion, UUID institucionId,
                         UUID campaniaId, boolean campaniaConfirmada, Instant fechaCaptacion, UUID operadorId,
                         List<UnidadVista> unidades) {

        static DonacionVista de(DonacionRegistrada r) {
            var d = r.donacion();
            return new DonacionVista(d.id(), d.donanteId(), d.intencionId(), d.tipoDonacion(), d.institucionId(),
                    d.campaniaId(), r.campaniaConfirmada(), d.fechaCaptacion(), d.operadorId(),
                    r.unidades().stream().map(UnidadVista::de).toList());
        }
    }

    record ElegibilidadVista(boolean elegible, LocalDate elegibleDesde, Long diasRestantes) {

        static ElegibilidadVista de(Elegibilidad e) {
            return new ElegibilidadVista(e.elegible(), e.elegibleDesde(), e.diasRestantes());
        }
    }

    record ExistenciaVista(UUID institucionId, String componente, String grupoSanguineo, long unidadesDisponibles,
                           LocalDate vencimientoMasProximo) {

        static ExistenciaVista de(Existencia e) {
            return new ExistenciaVista(e.institucionId(), e.componente(), e.grupoSanguineo(), e.unidadesDisponibles(),
                    e.vencimientoMasProximo());
        }
    }

    record UmbralVista(String componente, String grupoSanguineo, int minimoUnidades, int diasPreviosVencimiento,
                       Instant actualizadoEn) {

        static UmbralVista de(Umbral u) {
            return new UmbralVista(u.componente(), u.grupoSanguineo(), u.minimoUnidades(),
                    u.diasPreviosVencimiento(), u.actualizadoEn());
        }
    }

    record AlertaVista(UUID id, String tipo, UUID institucionId, String componente, String grupoSanguineo,
                       UUID unidadId, int valorObservado, String estado, Instant generadaEn, Instant cerradaEn) {

        static AlertaVista de(Alerta a) {
            return new AlertaVista(a.id(), a.tipo().codigo(), a.institucionId(), a.componente(), a.grupoSanguineo(),
                    a.unidadId(), a.valorObservado(), a.estado().codigo(), a.generadaEn(), a.cerradaEn());
        }
    }
}
