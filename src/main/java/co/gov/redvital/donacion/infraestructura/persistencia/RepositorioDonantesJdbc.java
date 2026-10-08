package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.dominio.modelo.Donante;
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
class RepositorioDonantesJdbc implements RepositorioDonantes {

    private static final String SELECCION = """
            SELECT d.id, d.usuario_id, d.documento_hash, d.nombre, d.correo, d.telefono, d.fecha_nacimiento,
                   d.municipio_ruta, g.codigo AS grupo, d.fecha_ultima_donacion, d.elegible_desde, d.activo
            FROM donante d LEFT JOIN grupo_sanguineo g ON g.id = d.grupo_sanguineo_id
            """;

    private final JdbcClient jdbc;

    RepositorioDonantesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Donante> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE d.id = :id").param("id", id).query(this::mapear).optional();
    }

    @Override
    public Optional<Donante> porUsuario(UUID usuarioId) {
        return jdbc.sql(SELECCION + " WHERE d.usuario_id = :usuario").param("usuario", usuarioId)
                .query(this::mapear).optional();
    }

    @Override
    public Optional<Donante> porDocumentoHash(String documentoHash) {
        return jdbc.sql(SELECCION + " WHERE d.documento_hash = :hash").param("hash", documentoHash)
                .query(this::mapear).optional();
    }

    @Override
    public void insertar(Donante d, Instant ahora) {
        jdbc.sql("""
                INSERT INTO donante (id, usuario_id, documento_hash, nombre, correo, telefono, fecha_nacimiento,
                                     municipio_ruta, grupo_sanguineo_id, fecha_ultima_donacion, elegible_desde,
                                     activo, creado_en, actualizado_en)
                VALUES (:id, :usuario, :hash, :nombre, :correo, :telefono, :nacimiento, :municipio,
                        (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo), :ultima, :elegible, :activo,
                        :ahora, :ahora)""")
                .param("id", d.id())
                .param("usuario", d.usuarioId())
                .param("hash", d.documentoHash())
                .param("nombre", d.nombre())
                .param("correo", d.correo())
                .param("telefono", d.telefono())
                .param("nacimiento", d.fechaNacimiento())
                .param("municipio", d.municipioRuta())
                .param("grupo", d.grupoSanguineo())
                .param("ultima", d.fechaUltimaDonacion())
                .param("elegible", d.elegibleDesde())
                .param("activo", d.activo())
                .param("ahora", Sql.ts(ahora))
                .update();
    }

    @Override
    public void actualizarContacto(UUID id, String nombre, String correo, String telefono, String municipioRuta,
                                   Instant ahora) {
        jdbc.sql("""
                UPDATE donante SET nombre = :nombre, correo = :correo, telefono = :telefono,
                                   municipio_ruta = :municipio, actualizado_en = :ahora
                WHERE id = :id""")
                .param("nombre", nombre)
                .param("correo", correo)
                .param("telefono", telefono)
                .param("municipio", municipioRuta)
                .param("ahora", Sql.ts(ahora))
                .param("id", id)
                .update();
    }

    @Override
    public void registrarDonacion(UUID id, LocalDate fecha, LocalDate elegibleDesde, String grupoSanguineo,
                                  Instant ahora) {
        jdbc.sql("""
                UPDATE donante SET fecha_ultima_donacion = :fecha, elegible_desde = :elegible,
                                   grupo_sanguineo_id = (SELECT id FROM grupo_sanguineo WHERE codigo = :grupo),
                                   actualizado_en = :ahora
                WHERE id = :id""")
                .param("fecha", fecha)
                .param("elegible", elegibleDesde)
                .param("grupo", grupoSanguineo)
                .param("ahora", Sql.ts(ahora))
                .param("id", id)
                .update();
    }

    @Override
    public List<UUID> conAvisosEnTerritorio(String territorioRuta) {
        return jdbc.sql("""
                SELECT d.id FROM donante d
                WHERE d.activo
                  AND (d.municipio_ruta = :ruta OR d.municipio_ruta LIKE :prefijo)
                  AND (SELECT c.otorgado FROM consentimiento c
                       WHERE c.donante_id = d.id AND c.finalidad = 'avisos_campanas'
                       ORDER BY c.creado_en DESC LIMIT 1) IS TRUE""")
                .param("ruta", territorioRuta)
                .param("prefijo", territorioRuta + "/%")
                .query((rs, n) -> Sql.uuid(rs, "id"))
                .list();
    }

    private Donante mapear(ResultSet rs, int fila) throws SQLException {
        return new Donante(Sql.uuid(rs, "id"), Sql.uuid(rs, "usuario_id"), rs.getString("documento_hash"),
                rs.getString("nombre"), rs.getString("correo"), rs.getString("telefono"),
                Sql.fecha(rs, "fecha_nacimiento"), rs.getString("municipio_ruta"), rs.getString("grupo"),
                Sql.fecha(rs, "fecha_ultima_donacion"), Sql.fecha(rs, "elegible_desde"), rs.getBoolean("activo"));
    }
}
