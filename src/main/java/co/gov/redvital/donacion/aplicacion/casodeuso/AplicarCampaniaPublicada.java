package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.eventos.AvisoSolicitadoV1;
import co.gov.redvital.donacion.aplicacion.eventos.CampaniaPublicadaV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.EventosProcesadosPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionCampaniasPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.modelo.CampaniaPublicada;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumidor idempotente de EV-03. Actualiza la proyección local de campañas
 * y, si los avisos están habilitados, pide un aviso por cada donante del
 * territorio que autorizó los avisos de campañas. Todo en una transacción con
 * la fila de evento_procesado: un duplicado se descarta sin efecto.
 */
public final class AplicarCampaniaPublicada {

    private static final Duration VIGENCIA_PROCESADO = Duration.ofDays(14);

    private final EventosProcesadosPort procesados;
    private final ProyeccionCampaniasPort proyeccion;
    private final RepositorioDonantes donantes;
    private final BandejaSalidaPort bandeja;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final boolean avisosHabilitados;
    private final Clock reloj;

    public AplicarCampaniaPublicada(EventosProcesadosPort procesados, ProyeccionCampaniasPort proyeccion,
                                    RepositorioDonantes donantes, BandejaSalidaPort bandeja,
                                    UnidadDeTrabajo unidadDeTrabajo, boolean avisosHabilitados, Clock reloj) {
        this.procesados = procesados;
        this.proyeccion = proyeccion;
        this.donantes = donantes;
        this.bandeja = bandeja;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.avisosHabilitados = avisosHabilitados;
        this.reloj = reloj;
    }

    /** @return falso si el evento ya se había aplicado */
    public boolean ejecutar(UUID eventoId, CampaniaPublicadaV1 evento, String correlacionId) {
        Validaciones.rutaTerritorio(evento.territorioRuta(), "territorio_ruta");
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            if (!procesados.registrarSiNuevo(Temas.GRUPO_DONACION, eventoId, CampaniaPublicadaV1.TIPO, ahora,
                    ahora.plus(VIGENCIA_PROCESADO))) {
                return false;
            }
            proyeccion.guardar(new CampaniaPublicada(evento.campaniaId(), evento.institucionId(), evento.nombre(),
                    evento.sede(), evento.territorioCodigo(), evento.territorioRuta(), evento.iniciaEn(),
                    evento.terminaEn(), evento.publicadaEn(), "publicada"), eventoId, ahora);

            if (avisosHabilitados && evento.terminaEn().isAfter(ahora)) {
                for (UUID donanteId : donantes.conAvisosEnTerritorio(evento.territorioRuta())) {
                    var aviso = new AvisoSolicitadoV1(UUID.randomUUID(), AvisoSolicitadoV1.AVISO_CAMPANIA_PUBLICADA,
                            "donante", donanteId, evento.campaniaId(), ahora);
                    bandeja.encolar(new BandejaSalidaPort.EventoSalida(aviso.avisoId(), AvisoSolicitadoV1.TIPO,
                            AvisoSolicitadoV1.VERSION, Temas.AVISO_SOLICITADO, donanteId.toString(), aviso,
                            "campania_publicada:" + evento.campaniaId() + ":" + donanteId, correlacionId), ahora);
                }
            }
            return true;
        });
    }
}
