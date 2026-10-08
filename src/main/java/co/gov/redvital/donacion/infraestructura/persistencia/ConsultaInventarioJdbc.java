package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.ConsultaInventarioPort;
import co.gov.redvital.donacion.dominio.modelo.Existencia;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Proyección de existencias sobre unidad (EC-15). El filtro es inclusivo: solo
 * cuenta lo que el catálogo marca cuenta_disponible, apto y no vencido; ante
 * cualquier duda la unidad no cuenta (RNI-04).
 */
@Repository
class ConsultaInventarioJdbc implements ConsultaInventarioPort {

    private static final String DISPONIBLES = """
            FROM unidad u
            JOIN estado_unidad e ON e.id = u.estado_id AND e.cuenta_disponible
            JOIN componente c ON c.id = u.componente_id
            JOIN grupo_sanguineo g ON g.id = u.grupo_sanguineo_id
            WHERE u.institucion_custodia_id = :institucion
              AND u.apta IS TRUE
              AND u.fecha_vencimiento >= :hoy
              AND (CAST(:componente AS varchar) IS NULL OR c.codigo = :componente)
              AND (CAST(:grupo AS varchar) IS NULL OR g.codigo = :grupo)
            """;

    private final JdbcClient jdbc;

    ConsultaInventarioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Existencia> existencias(UUID institucionId, String componente, String grupo, LocalDate hoy) {
        return jdbc.sql("SELECT c.codigo AS componente, g.codigo AS grupo, count(*) AS cantidad, "
                        + "min(u.fecha_vencimiento) AS vencimiento " + DISPONIBLES
                        + " GROUP BY c.codigo, g.codigo ORDER BY c.codigo, g.codigo")
                .param("institucion", institucionId)
                .param("hoy", hoy)
                .param("componente", componente)
                .param("grupo", grupo)
                .query((rs, n) -> new Existencia(institucionId, rs.getString("componente"), rs.getString("grupo"),
                        rs.getLong("cantidad"), Sql.fecha(rs, "vencimiento")))
                .list();
    }

    @Override
    public long disponibles(UUID institucionId, String componente, String grupo, LocalDate hoy) {
        return jdbc.sql("SELECT count(*) " + DISPONIBLES)
                .param("institucion", institucionId)
                .param("hoy", hoy)
                .param("componente", componente)
                .param("grupo", grupo)
                .query(Long.class).single();
    }
}
