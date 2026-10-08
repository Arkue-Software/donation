package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioIntenciones;
import co.gov.redvital.donacion.dominio.modelo.EstadoIntencion;
import co.gov.redvital.donacion.dominio.modelo.IntencionDonacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioIntencionesJdbc implements RepositorioIntenciones {

    private static final String SELECCION = """
            SELECT i.id, i.codigo, g.codigo AS grupo, i.municipio_ruta, i.estado, i.creado_en, i.expira_en,
                   i.atendida_en
            FROM intencion_donacion i LEFT JOIN grupo_sanguineo g ON g.id = i.grupo_sanguineo_id
            """;

    private final JdbcClient jdbc;

    RepositorioIntencionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(IntencionDonacion i) {
        jdbc.sql("""
                INSERT INTO intencion_donacion (id, codigo, grupo_sanguineo_id, municipio_ruta, estado, creado_en, expira_en)
                VALUES (:id, :codigo, (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo), :municipio, :estado,
                        :creado, :expira)""")
                .param("id", i.id())
                .param("codigo", i.codigo())
                .param("grupo", i.grupoSanguineo())
                .param("municipio", i.municipioRuta())
                .param("estado", i.estado().codigo())
                .param("creado", Sql.ts(i.creadoEn()))
                .param("expira", Sql.ts(i.expiraEn()))
                .update();
    }

    @Override
    public Optional<IntencionDonacion> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE i.id = :id").param("id", id).query(this::mapear).optional();
    }

    @Override
    public Optional<IntencionDonacion> porCodigo(String codigo) {
        return jdbc.sql(SELECCION + " WHERE i.codigo = :codigo").param("codigo", codigo).query(this::mapear).optional();
    }

    @Override
    public Optional<IntencionDonacion> porCodigoParaActualizar(String codigo) {
        return jdbc.sql(SELECCION + " WHERE i.codigo = :codigo FOR UPDATE OF i").param("codigo", codigo)
                .query(this::mapear).optional();
    }

    @Override
    public void marcarAtendida(UUID id, Instant ahora) {
        jdbc.sql("UPDATE intencion_donacion SET estado = 'atendida', atendida_en = :ahora WHERE id = :id")
                .param("ahora", Sql.ts(ahora)).param("id", id).update();
    }

    @Override
    public void marcarCaducada(UUID id) {
        jdbc.sql("UPDATE intencion_donacion SET estado = 'caducada' WHERE id = :id AND estado = 'pendiente'")
                .param("id", id).update();
    }

    private IntencionDonacion mapear(ResultSet rs, int fila) throws SQLException {
        return new IntencionDonacion(Sql.uuid(rs, "id"), rs.getString("codigo"), rs.getString("grupo"),
                rs.getString("municipio_ruta"), EstadoIntencion.desdeCodigo(rs.getString("estado")),
                Sql.instante(rs, "creado_en"), Sql.instante(rs, "expira_en"), Sql.instante(rs, "atendida_en"));
    }
}
