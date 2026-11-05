package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUmbrales;
import co.gov.redvital.donacion.dominio.modelo.Umbral;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioUmbralesJdbc implements RepositorioUmbrales {

    private static final String SELECCION = """
            SELECT u.institucion_id, c.codigo AS componente, g.codigo AS grupo, u.minimo_unidades,
                   u.dias_previos_vencimiento, u.actualizado_por, u.actualizado_en
            FROM umbral_inventario u
            JOIN componente c ON c.id = u.componente_id
            LEFT JOIN grupo_sanguineo g ON g.id = u.grupo_sanguineo_id
            """;

    private final JdbcClient jdbc;

    RepositorioUmbralesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Umbral> deInstitucion(UUID institucionId) {
        return jdbc.sql(SELECCION + " WHERE u.institucion_id = :institucion ORDER BY c.codigo, g.codigo NULLS FIRST")
                .param("institucion", institucionId).query(this::mapear).list();
    }

    @Override
    public List<Umbral> todos() {
        return jdbc.sql(SELECCION).query(this::mapear).list();
    }

    @Override
    public void guardar(Umbral u) {
        jdbc.sql("""
                INSERT INTO umbral_inventario (id, institucion_id, componente_id, grupo_sanguineo_id, minimo_unidades,
                                               dias_previos_vencimiento, actualizado_por, actualizado_en)
                VALUES (:id, :institucion, (SELECT id FROM componente WHERE codigo = :componente),
                        (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo), :minimo, :dias, :usuario, :ahora)
                ON CONFLICT ON CONSTRAINT uq_umbral DO UPDATE SET
                    minimo_unidades = EXCLUDED.minimo_unidades,
                    dias_previos_vencimiento = EXCLUDED.dias_previos_vencimiento,
                    actualizado_por = EXCLUDED.actualizado_por,
                    actualizado_en = EXCLUDED.actualizado_en""")
                .param("id", UUID.randomUUID())
                .param("institucion", u.institucionId())
                .param("componente", u.componente())
                .param("grupo", u.grupoSanguineo())
                .param("minimo", u.minimoUnidades())
                .param("dias", u.diasPreviosVencimiento())
                .param("usuario", u.actualizadoPor())
                .param("ahora", Sql.ts(u.actualizadoEn()))
                .update();
    }

    private Umbral mapear(ResultSet rs, int fila) throws SQLException {
        return new Umbral(Sql.uuid(rs, "institucion_id"), rs.getString("componente"), rs.getString("grupo"),
                rs.getInt("minimo_unidades"), rs.getInt("dias_previos_vencimiento"), Sql.uuid(rs, "actualizado_por"),
                Sql.instante(rs, "actualizado_en"));
    }
}
