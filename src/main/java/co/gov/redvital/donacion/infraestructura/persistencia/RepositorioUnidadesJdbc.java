package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.comun.Pagina;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioUnidadesJdbc implements RepositorioUnidades {

    private static final String SELECCION = """
            SELECT u.id, u.donacion_id, c.codigo AS componente, g.codigo AS grupo, u.institucion_custodia_id,
                   e.codigo AS estado, u.apta, u.fecha_vencimiento, u.volumen_ml, d.fecha_captacion
            FROM unidad u
            JOIN componente c ON c.id = u.componente_id
            JOIN grupo_sanguineo g ON g.id = u.grupo_sanguineo_id
            JOIN estado_unidad e ON e.id = u.estado_id
            JOIN donacion d ON d.id = u.donacion_id
            """;

    private final JdbcClient jdbc;

    RepositorioUnidadesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(Unidad u, Instant ahora) {
        jdbc.sql("""
                INSERT INTO unidad (id, donacion_id, componente_id, grupo_sanguineo_id, institucion_custodia_id,
                                    estado_id, apta, fecha_vencimiento, volumen_ml, creado_en, actualizado_en)
                VALUES (:id, :donacion,
                        (SELECT id FROM componente WHERE codigo = :componente),
                        (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo),
                        :institucion,
                        (SELECT id FROM estado_unidad WHERE codigo = :estado),
                        :apta, :vencimiento, :volumen, :ahora, :ahora)""")
                .param("id", u.id())
                .param("donacion", u.donacionId())
                .param("componente", u.componente())
                .param("grupo", u.grupoSanguineo())
                .param("institucion", u.institucionCustodiaId())
                .param("estado", u.estado().codigo())
                .param("apta", u.apta())
                .param("vencimiento", u.fechaVencimiento())
                .param("volumen", u.volumenMl())
                .param("ahora", Sql.ts(ahora))
                .update();
    }

    @Override
    public void actualizarEstado(Unidad u, Instant ahora) {
        jdbc.sql("""
                UPDATE unidad
                SET estado_id = (SELECT id FROM estado_unidad WHERE codigo = :estado),
                    apta = :apta,
                    actualizado_en = :ahora
                WHERE id = :id""")
                .param("estado", u.estado().codigo())
                .param("apta", u.apta())
                .param("ahora", Sql.ts(ahora))
                .param("id", u.id())
                .update();
    }

    @Override
    public Optional<Unidad> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE u.id = :id").param("id", id).query(this::mapear).optional();
    }

    @Override
    public Optional<Unidad> buscarParaActualizar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE u.id = :id FOR UPDATE OF u").param("id", id).query(this::mapear).optional();
    }

    @Override
    public List<Unidad> deDonacion(UUID donacionId) {
        return jdbc.sql(SELECCION + " WHERE u.donacion_id = :donacion ORDER BY c.codigo")
                .param("donacion", donacionId).query(this::mapear).list();
    }

    @Override
    public List<Unidad> deDonacionParaActualizar(UUID donacionId) {
        return jdbc.sql(SELECCION + " WHERE u.donacion_id = :donacion ORDER BY c.codigo FOR UPDATE OF u")
                .param("donacion", donacionId).query(this::mapear).list();
    }

    @Override
    public Pagina<Unidad> listar(UUID institucionId, CodigoEstado estado, String componente, int pagina, int tamano) {
        String filtro = """
                 WHERE u.institucion_custodia_id = :institucion
                   AND (CAST(:estado AS varchar) IS NULL OR e.codigo = :estado)
                   AND (CAST(:componente AS varchar) IS NULL OR c.codigo = :componente)
                """;
        String codigoEstado = estado == null ? null : estado.codigo();
        long total = jdbc.sql("""
                SELECT count(*) FROM unidad u
                JOIN componente c ON c.id = u.componente_id
                JOIN estado_unidad e ON e.id = u.estado_id
                """ + filtro)
                .param("institucion", institucionId)
                .param("estado", codigoEstado)
                .param("componente", componente)
                .query(Long.class).single();
        List<Unidad> elementos = jdbc.sql(SELECCION + filtro
                        + " ORDER BY d.fecha_captacion DESC, u.id LIMIT :limite OFFSET :desplazamiento")
                .param("institucion", institucionId)
                .param("estado", codigoEstado)
                .param("componente", componente)
                .param("limite", tamano)
                .param("desplazamiento", (long) pagina * tamano)
                .query(this::mapear).list();
        return new Pagina<>(elementos, total, pagina, tamano);
    }

    @Override
    public List<Unidad> vencidasParaActualizar(LocalDate hoy, int limite) {
        return jdbc.sql(SELECCION + """
                 WHERE e.codigo IN ('fraccionada', 'en_tamizaje', 'disponible', 'reservada')
                   AND u.fecha_vencimiento < :hoy
                 ORDER BY u.fecha_vencimiento
                 LIMIT :limite
                 FOR UPDATE OF u SKIP LOCKED""")
                .param("hoy", hoy)
                .param("limite", limite)
                .query(this::mapear).list();
    }

    @Override
    public List<Unidad> disponiblesDe(UUID institucionId, LocalDate hoy) {
        return jdbc.sql(SELECCION + """
                 WHERE u.institucion_custodia_id = :institucion
                   AND e.cuenta_disponible AND u.apta IS TRUE AND u.fecha_vencimiento >= :hoy""")
                .param("institucion", institucionId)
                .param("hoy", hoy)
                .query(this::mapear).list();
    }

    private Unidad mapear(ResultSet rs, int fila) throws SQLException {
        return new Unidad(Sql.uuid(rs, "id"), Sql.uuid(rs, "donacion_id"), rs.getString("componente"),
                rs.getString("grupo"), Sql.uuid(rs, "institucion_custodia_id"),
                CodigoEstado.desdeCodigo(rs.getString("estado")), Sql.booleano(rs, "apta"),
                Sql.fecha(rs, "fecha_vencimiento"), Sql.entero(rs, "volumen_ml"), Sql.instante(rs, "fecha_captacion"));
    }
}
