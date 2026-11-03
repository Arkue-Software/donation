package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.eventos.InstitucionCambiadaV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.EventosProcesadosPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionInstitucionesPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Consumo de EV-07 {@code institution.changed}: actualiza la proyección local
 * de instituciones, con la que Donación resuelve qué bancos caen en una ruta y
 * cómo se llaman. Idempotente: un duplicado se descarta, y un cambio más viejo
 * que el que ya está guardado no lo pisa.
 */
public final class AplicarInstitucionCambiada {

    private static final Duration VIGENCIA_PROCESADO = Duration.ofDays(14);
    private static final Set<String> TIPOS = Set.of("banco_de_sangre", "servicio_transfusion");

    private final EventosProcesadosPort procesados;
    private final ProyeccionInstitucionesPort proyeccion;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public AplicarInstitucionCambiada(EventosProcesadosPort procesados, ProyeccionInstitucionesPort proyeccion,
                                      UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.procesados = procesados;
        this.proyeccion = proyeccion;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.reloj = reloj;
    }

    /** @return falso si el evento ya se había aplicado */
    public boolean ejecutar(UUID eventoId, InstitucionCambiadaV1 e) {
        Validaciones.rutaMunicipio(e.territorioRuta(), "territorio_ruta");
        if (!TIPOS.contains(e.tipo())) {
            throw new DatosInvalidos("El tipo de institución no es válido.");
        }
        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            if (!procesados.registrarSiNuevo(Temas.GRUPO_DONACION, eventoId, InstitucionCambiadaV1.TIPO, ahora,
                    ahora.plus(VIGENCIA_PROCESADO))) {
                return false;
            }
            proyeccion.guardar(new ProyeccionInstitucionesPort.Institucion(e.institucionId(), e.codigo(), e.nombre(),
                    e.tipo(), e.territorioCodigo(), e.territorioRuta(), e.vigente(), e.cambiadoEn()), eventoId, ahora);
            return true;
        });
    }
}
