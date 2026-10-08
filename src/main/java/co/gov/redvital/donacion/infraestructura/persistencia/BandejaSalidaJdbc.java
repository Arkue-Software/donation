package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bandeja de salida sobre evento_salida. La carga se serializa con las mismas
 * convenciones del contrato (snake_case, fechas ISO-8601 en UTC).
 */
@Repository
class BandejaSalidaJdbc implements BandejaSalidaPort {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    BandejaSalidaJdbc(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void encolar(EventoSalida e, Instant ahora) {
        jdbc.sql("""
                INSERT INTO evento_salida (id, tipo, version_esquema, tema, clave, carga, clave_natural, correlacion_id,
                                           estado, intentos_publicacion, creado_en)
                VALUES (:id, :tipo, :version, :tema, :clave, CAST(:carga AS jsonb), :claveNatural, :correlacion,
                        'pendiente', 0, :ahora)
                ON CONFLICT (clave_natural) DO NOTHING""")
                .param("id", e.id())
                .param("tipo", e.tipo())
                .param("version", e.versionEsquema())
                .param("tema", e.tema())
                .param("clave", e.clave())
                .param("carga", json.writeValueAsString(e.carga()))
                .param("claveNatural", e.claveNatural())
                .param("correlacion", e.correlacionId())
                .param("ahora", Sql.ts(ahora))
                .update();
    }

    @Override
    public List<EventoPendiente> pendientes(int limite) {
        return jdbc.sql("""
                SELECT id, tipo, version_esquema, tema, clave, carga::text AS carga, clave_natural IS NOT NULL AS natural,
                       correlacion_id, creado_en
                FROM evento_salida WHERE estado = 'pendiente'
                ORDER BY secuencia LIMIT :limite""")
                .param("limite", limite)
                .query((rs, n) -> new EventoPendiente(Sql.uuid(rs, "id"), rs.getString("tipo"),
                        rs.getInt("version_esquema"), rs.getString("tema"), rs.getString("clave"),
                        rs.getString("carga"), rs.getBoolean("natural"), rs.getString("correlacion_id"),
                        Sql.instante(rs, "creado_en")))
                .list();
    }

    @Override
    public void marcarPublicado(UUID id, Instant publicadoEn, Instant expiraEn) {
        jdbc.sql("""
                UPDATE evento_salida SET estado = 'publicado', publicado_en = :publicado, expira_en = :expira
                WHERE id = :id AND estado = 'pendiente'""")
                .param("publicado", Sql.ts(publicadoEn))
                .param("expira", Sql.ts(expiraEn))
                .param("id", id)
                .update();
    }

    @Override
    public void registrarFallo(UUID id) {
        jdbc.sql("UPDATE evento_salida SET intentos_publicacion = intentos_publicacion + 1 WHERE id = :id")
                .param("id", id).update();
    }

    @Override
    public int depurar(Instant ahora) {
        return jdbc.sql("DELETE FROM evento_salida WHERE estado = 'publicado' AND expira_en < :ahora")
                .param("ahora", Sql.ts(ahora)).update();
    }

    @Override
    public long antiguedadPendienteMasViejo(Instant ahora) {
        return jdbc.sql("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (:ahora - min(creado_en)))::bigint, 0)
                FROM evento_salida WHERE estado = 'pendiente'""")
                .param("ahora", Sql.ts(ahora))
                .query(Long.class).single();
    }
}
