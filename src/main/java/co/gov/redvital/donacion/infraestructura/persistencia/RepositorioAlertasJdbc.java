package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioAlertas;
import co.gov.redvital.donacion.dominio.modelo.Alerta;
import co.gov.redvital.donacion.dominio.modelo.EstadoAlerta;
import co.gov.redvital.donacion.dominio.modelo.TipoAlerta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioAlertasJdbc implements RepositorioAlertas {

    private static final String SELECCION = """
            SELECT a.id, a.tipo, a.institucion_id, c.codigo AS componente, g.codigo AS grupo, a.unidad_id,
                   a.valor_observado, a.estado, a.generada_en, a.cerrada_en, a.atendida_por
            FROM alerta a
            JOIN componente c ON c.id = a.componente_id
            LEFT JOIN grupo_sanguineo g ON g.id = a.grupo_sanguineo_id
            """;

    private final JdbcClient jdbc;

    RepositorioAlertasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Alerta> deInstitucion(UUID institucionId, EstadoAlerta estado) {
        return jdbc.sql(SELECCION + """
                 WHERE a.institucion_id = :institucion
                   AND (CAST(:estado AS varchar) IS NULL OR a.estado = :estado)
                   AND (a.estado = 'abierta' OR a.generada_en > now() - INTERVAL '30 days')
                 ORDER BY (a.estado = 'abierta') DESC, a.generada_en DESC""")
                .param("institucion", institucionId)
                .param("estado", estado == null ? null : estado.codigo())
                .query(this::mapear).list();
    }

    @Override
    public Optional<Alerta> buscarParaActualizar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id FOR UPDATE OF a").param("id", id).query(this::mapear).optional();
    }

    @Override
    public List<Alerta> abiertas(TipoAlerta tipo) {
        return jdbc.sql(SELECCION + " WHERE a.tipo = :tipo AND a.estado = 'abierta'")
                .param("tipo", tipo.codigo()).query(this::mapear).list();
    }

    @Override
    public void abrirOActualizar(Alerta a) {
        jdbc.sql("""
                INSERT INTO alerta (id, tipo, institucion_id, componente_id, grupo_sanguineo_id, unidad_id,
                                    valor_observado, estado, generada_en)
                VALUES (:id, :tipo, :institucion, (SELECT id FROM componente WHERE codigo = :componente),
                        (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo), :unidad, :valor, 'abierta', :generada)
                ON CONFLICT (tipo, institucion_id, componente_id, grupo_sanguineo_id, unidad_id)
                    WHERE estado = 'abierta'
                DO UPDATE SET valor_observado = EXCLUDED.valor_observado""")
                .param("id", a.id())
                .param("tipo", a.tipo().codigo())
                .param("institucion", a.institucionId())
                .param("componente", a.componente())
                .param("grupo", a.grupoSanguineo())
                .param("unidad", a.unidadId())
                .param("valor", a.valorObservado())
                .param("generada", Sql.ts(a.generadaEn()))
                .update();
    }

    @Override
    public void cerrar(UUID id, EstadoAlerta estado, Instant ahora, UUID atendidaPor) {
        jdbc.sql("""
                UPDATE alerta SET estado = :estado, cerrada_en = :ahora, atendida_por = :atendidaPor
                WHERE id = :id AND estado = 'abierta'""")
                .param("estado", estado.codigo())
                .param("ahora", Sql.ts(ahora))
                .param("atendidaPor", atendidaPor)
                .param("id", id)
                .update();
    }

    private Alerta mapear(ResultSet rs, int fila) throws SQLException {
        return new Alerta(Sql.uuid(rs, "id"), TipoAlerta.desdeCodigo(rs.getString("tipo")),
                Sql.uuid(rs, "institucion_id"), rs.getString("componente"), rs.getString("grupo"),
                Sql.uuid(rs, "unidad_id"), rs.getInt("valor_observado"), EstadoAlerta.desdeCodigo(rs.getString("estado")),
                Sql.instante(rs, "generada_en"), Sql.instante(rs, "cerrada_en"), Sql.uuid(rs, "atendida_por"));
    }
}
