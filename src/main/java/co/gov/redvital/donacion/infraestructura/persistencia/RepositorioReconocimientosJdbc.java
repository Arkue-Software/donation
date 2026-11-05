package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioReconocimientos;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioReconocimientosJdbc implements RepositorioReconocimientos {

    private final JdbcClient jdbc;

    RepositorioReconocimientosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Instant> otorgados(UUID donanteId) {
        Map<String, Instant> resultado = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT r.codigo, rd.otorgado_en FROM reconocimiento_donante rd
                JOIN reconocimiento r ON r.id = rd.reconocimiento_id
                WHERE rd.donante_id = :donante""")
                .param("donante", donanteId)
                .query(rs -> {
                    resultado.put(rs.getString("codigo"), Sql.instante(rs, "otorgado_en"));
                });
        return resultado;
    }

    @Override
    public void otorgar(UUID donanteId, UUID reconocimientoId, UUID donacionId, Instant ahora) {
        jdbc.sql("""
                INSERT INTO reconocimiento_donante (donante_id, reconocimiento_id, donacion_id, otorgado_en)
                VALUES (:donante, :reconocimiento, :donacion, :ahora)
                ON CONFLICT DO NOTHING""")
                .param("donante", donanteId)
                .param("reconocimiento", reconocimientoId)
                .param("donacion", donacionId)
                .param("ahora", Sql.ts(ahora))
                .update();
    }
}
