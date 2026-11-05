package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.IndicadoresPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.PublicacionIndicadoresPort;
import java.time.Instant;
import java.util.Optional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Agregados para M7 calculados sobre las tablas del ciclo de vida, nunca
 * almacenados (el inventario es una proyección, DD 3.1). Solo salen conteos.
 */
@Repository
class IndicadoresJdbc implements IndicadoresPort, PublicacionIndicadoresPort {

    /** Una unidad cuenta como no apta o vencida si alguna vez pasó a ese estado. */
    private static final String PASO_A = """
            EXISTS (SELECT 1 FROM evento_unidad ev JOIN estado_unidad en ON en.id = ev.estado_nuevo_id
                    WHERE ev.unidad_id = u.id AND en.codigo = '%s')""";

    private final JdbcClient jdbc;
    private final ZoneId zona;

    IndicadoresJdbc(JdbcClient jdbc, ZoneId zona) {
        this.jdbc = jdbc;
        this.zona = zona;
    }

    @Override
    public List<Periodo> captacion(Set<UUID> instituciones, LocalDate desde, LocalDate hasta, Agrupacion agrupacion) {
        String unidad = switch (agrupacion) {
            case DIA -> "day";
            case SEMANA -> "week";
            case MES -> "month";
        };
        return jdbc.sql("""
                        SELECT CAST(date_trunc(:unidad, d.fecha_captacion AT TIME ZONE :zona) AS date) AS inicio,
                               count(DISTINCT d.id) AS donaciones,
                               count(u.id) AS unidades,
                               count(u.id) FILTER (WHERE %s) AS no_aptas,
                               count(u.id) FILTER (WHERE %s) AS vencidas
                        FROM donacion d
                        LEFT JOIN unidad u ON u.donacion_id = d.id
                        WHERE d.institucion_id IN (:instituciones)
                          AND d.fecha_captacion >= CAST(CAST(:desde AS date) AS timestamp) AT TIME ZONE :zona
                          AND d.fecha_captacion < CAST(CAST(:hasta AS date) + 1 AS timestamp) AT TIME ZONE :zona
                        GROUP BY 1
                        ORDER BY 1""".formatted(PASO_A.formatted("no_apta"), PASO_A.formatted("vencida")))
                .param("unidad", unidad)
                .param("zona", zona.getId())
                .param("instituciones", List.copyOf(instituciones))
                .param("desde", desde)
                .param("hasta", hasta)
                .query((rs, n) -> new Periodo(Sql.fecha(rs, "inicio"), rs.getLong("donaciones"), rs.getLong("unidades"),
                        rs.getLong("no_aptas"), rs.getLong("vencidas")))
                .list();
    }

    /**
     * Existencias por institución, componente y grupo, con las que vencen
     * dentro del plazo del umbral (siete días si no hay umbral) y la marca de
     * bajo umbral. Un umbral sin existencias también aparece, con cero: es
     * justo el caso que más importa ver.
     */
    @Override
    public List<Existencias> inventario(Set<UUID> instituciones, String componente, String grupo, LocalDate hoy) {
        return jdbc.sql("""
                        WITH disponibles AS (
                            SELECT u.institucion_custodia_id AS institucion, u.componente_id, u.grupo_sanguineo_id,
                                   u.fecha_vencimiento
                            FROM unidad u
                            JOIN estado_unidad e ON e.id = u.estado_id AND e.cuenta_disponible
                            WHERE u.institucion_custodia_id IN (:instituciones)
                              AND u.apta IS TRUE
                              AND u.fecha_vencimiento >= :hoy
                        ),
                        combinaciones AS (
                            SELECT institucion, componente_id, grupo_sanguineo_id FROM disponibles
                            UNION
                            SELECT institucion_id, componente_id, grupo_sanguineo_id FROM umbral_inventario
                            WHERE institucion_id IN (:instituciones) AND grupo_sanguineo_id IS NOT NULL
                        )
                        SELECT k.institucion, c.codigo AS componente, g.codigo AS grupo,
                               count(d.fecha_vencimiento) AS disponibles,
                               count(d.fecha_vencimiento) FILTER (
                                   WHERE d.fecha_vencimiento <= CAST(:hoy AS date) + coalesce(t.dias_previos_vencimiento, 7))
                                   AS por_vencer,
                               t.minimo_unidades AS minimo
                        FROM combinaciones k
                        JOIN componente c ON c.id = k.componente_id
                        JOIN grupo_sanguineo g ON g.id = k.grupo_sanguineo_id
                        LEFT JOIN disponibles d ON d.institucion = k.institucion AND d.componente_id = k.componente_id
                                                AND d.grupo_sanguineo_id = k.grupo_sanguineo_id
                        LEFT JOIN umbral_inventario t ON t.institucion_id = k.institucion
                                                     AND t.componente_id = k.componente_id
                                                     AND t.grupo_sanguineo_id = k.grupo_sanguineo_id
                        WHERE (CAST(:componente AS varchar) IS NULL OR c.codigo = :componente)
                          AND (CAST(:grupo AS varchar) IS NULL OR g.codigo = :grupo)
                        GROUP BY k.institucion, c.codigo, g.codigo, t.dias_previos_vencimiento, t.minimo_unidades
                        ORDER BY k.institucion, c.codigo, g.codigo""")
                .param("instituciones", List.copyOf(instituciones))
                .param("hoy", hoy)
                .param("componente", componente)
                .param("grupo", grupo)
                .query((rs, n) -> {
                    long disponibles = rs.getLong("disponibles");
                    Integer minimo = Sql.entero(rs, "minimo");
                    return new Existencias(Sql.uuid(rs, "institucion"), rs.getString("componente"), rs.getString("grupo"),
                            disponibles, rs.getLong("por_vencer"), minimo != null && disponibles < minimo);
                })
                .list();
    }

    @Override
    public List<UUID> institucionesConDatos() {
        return jdbc.sql("""
                        SELECT institucion_id FROM donacion
                        UNION SELECT institucion_custodia_id FROM unidad
                        UNION SELECT institucion_id FROM umbral_inventario""")
                .query((rs, n) -> Sql.uuid(rs, "institucion_id"))
                .list();
    }

    @Override
    public Optional<String> huellaPublicada(UUID institucionId) {
        return jdbc.sql("SELECT huella FROM indicador_publicado WHERE institucion_id = :id")
                .param("id", institucionId)
                .query(String.class)
                .optional();
    }

    @Override
    public void registrarPublicacion(UUID institucionId, String huella, Instant ahora) {
        jdbc.sql("""
                        INSERT INTO indicador_publicado (institucion_id, huella, publicado_en) VALUES (:id, :huella, :ahora)
                        ON CONFLICT (institucion_id) DO UPDATE SET huella = EXCLUDED.huella, publicado_en = EXCLUDED.publicado_en""")
                .param("id", institucionId)
                .param("huella", huella)
                .param("ahora", Sql.ts(ahora))
                .update();
    }
}
