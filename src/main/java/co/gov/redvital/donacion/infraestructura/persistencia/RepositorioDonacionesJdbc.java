package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.dominio.modelo.Donacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioDonacionesJdbc implements RepositorioDonaciones {

    private static final String SELECCION = """
            SELECT d.id, d.donante_id, d.intencion_id, t.codigo AS tipo, d.institucion_id, d.campania_id,
                   d.fecha_captacion, d.operador_id
            FROM donacion d JOIN tipo_donacion t ON t.id = d.tipo_donacion_id
            """;

    private final JdbcClient jdbc;

    RepositorioDonacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(Donacion d) {
        jdbc.sql("""
                INSERT INTO donacion (id, donante_id, intencion_id, tipo_donacion_id, institucion_id, campania_id,
                                      fecha_captacion, operador_id, creado_en)
                VALUES (:id, :donante, :intencion, (SELECT id FROM tipo_donacion WHERE codigo = :tipo),
                        :institucion, :campania, :captacion, :operador, :captacion)""")
                .param("id", d.id())
                .param("donante", d.donanteId())
                .param("intencion", d.intencionId())
                .param("tipo", d.tipoDonacion())
                .param("institucion", d.institucionId())
                .param("campania", d.campaniaId())
                .param("captacion", Sql.ts(d.fechaCaptacion()))
                .param("operador", d.operadorId())
                .update();
    }

    @Override
    public Optional<Donacion> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE d.id = :id").param("id", id).query(this::mapear).optional();
    }

    @Override
    public List<Donacion> deDonante(UUID donanteId) {
        return jdbc.sql(SELECCION + " WHERE d.donante_id = :donante ORDER BY d.fecha_captacion DESC")
                .param("donante", donanteId).query(this::mapear).list();
    }

    @Override
    public int contarDeDonante(UUID donanteId) {
        return jdbc.sql("SELECT count(*) FROM donacion WHERE donante_id = :donante")
                .param("donante", donanteId).query(Integer.class).single();
    }

    private Donacion mapear(ResultSet rs, int fila) throws SQLException {
        return new Donacion(Sql.uuid(rs, "id"), Sql.uuid(rs, "donante_id"), Sql.uuid(rs, "intencion_id"),
                rs.getString("tipo"), Sql.uuid(rs, "institucion_id"), Sql.uuid(rs, "campania_id"),
                Sql.instante(rs, "fecha_captacion"), Sql.uuid(rs, "operador_id"));
    }
}
