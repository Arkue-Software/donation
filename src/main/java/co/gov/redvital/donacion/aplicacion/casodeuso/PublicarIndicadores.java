package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.eventos.IndicadoresActualizadosV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.HuellaPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.IndicadoresPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.PublicacionIndicadoresPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Proceso programado que publica EV-09 {@code indicators.updated}: por cada
 * institución, la captación por día de los últimos dos años y sus existencias.
 * Solo publica cuando la fotografía cambió, comparando su huella con la de la
 * última publicada. Así el Servicio Institucional arma los indicadores (M7)
 * con su propia copia, sin consultar a Donación, y una fotografía repetida no
 * duplica nada porque reemplaza la anterior.
 */
public final class PublicarIndicadores {

    public static final long CLAVE_BLOQUEO = 0x52564d37L; // "RVM7"
    public static final int DIAS = 731;

    private final IndicadoresPort indicadores;
    private final PublicacionIndicadoresPort publicaciones;
    private final HuellaPeticion huellas;
    private final BandejaSalidaPort bandeja;
    private final BloqueoConsultivoPort bloqueo;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final ZoneId zona;
    private final Clock reloj;

    public PublicarIndicadores(IndicadoresPort indicadores, PublicacionIndicadoresPort publicaciones,
                               HuellaPeticion huellas, BandejaSalidaPort bandeja, BloqueoConsultivoPort bloqueo,
                               UnidadDeTrabajo unidadDeTrabajo, ZoneId zona, Clock reloj) {
        this.indicadores = indicadores;
        this.publicaciones = publicaciones;
        this.huellas = huellas;
        this.bandeja = bandeja;
        this.bloqueo = bloqueo;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.zona = zona;
        this.reloj = reloj;
    }

    /** @return cuántas instituciones se publicaron */
    public int ejecutar() {
        return unidadDeTrabajo.ejecutar(() -> {
            if (!bloqueo.intentar(CLAVE_BLOQUEO)) {
                return 0;
            }
            Instant ahora = reloj.instant();
            LocalDate hasta = ahora.atZone(zona).toLocalDate();
            LocalDate desde = hasta.minusDays(DIAS - 1L);
            int publicadas = 0;
            for (UUID institucion : publicaciones.institucionesConDatos()) {
                Set<UUID> una = Set.of(institucion);
                List<IndicadoresActualizadosV1.Dia> captacion = indicadores
                        .captacion(una, desde, hasta, IndicadoresPort.Agrupacion.DIA).stream()
                        .map(p -> new IndicadoresActualizadosV1.Dia(p.inicio(), p.donaciones(), p.unidadesGeneradas(),
                                p.unidadesNoAptas(), p.unidadesVencidas()))
                        .toList();
                List<IndicadoresActualizadosV1.Existencias> inventario = indicadores
                        .inventario(una, null, null, hasta).stream()
                        .map(e -> new IndicadoresActualizadosV1.Existencias(e.componente(), e.grupoSanguineo(),
                                e.unidadesDisponibles(), e.unidadesPorVencer(), e.bajoUmbral()))
                        .toList();
                // La huella cubre los datos, no la fecha: el mismo día sin cambios no se republica.
                String huella = huellas.calcular(List.of(desde, captacion, inventario));
                if (publicaciones.huellaPublicada(institucion).filter(huella::equals).isPresent()) {
                    continue;
                }
                var evento = new IndicadoresActualizadosV1(institucion, ahora, desde, hasta, captacion, inventario);
                bandeja.encolar(new BandejaSalidaPort.EventoSalida(UUID.randomUUID(), IndicadoresActualizadosV1.TIPO,
                        IndicadoresActualizadosV1.VERSION, Temas.INDICADORES_ACTUALIZADOS, institucion.toString(),
                        evento, null, UUID.randomUUID().toString()), ahora);
                publicaciones.registrarPublicacion(institucion, huella, ahora);
                publicadas++;
            }
            return publicadas;
        });
    }
}
