package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioConsentimientos;
import co.gov.redvital.donacion.dominio.modelo.Consentimiento;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioConsentimientosJdbc implements RepositorioConsentimientos {

    private final JdbcClient jdbc;

    RepositorioConsentimientosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(UUID donanteId, Finalidad finalidad, boolean otorgado, String versionAviso, Instant ahora) {
        jdbc.sql("""
                INSERT INTO consentimiento (id, donante_id, finalidad, version_aviso, otorgado, creado_en)
                VALUES (:id, :donante, :finalidad, :version, :otorgado, :ahora)""")
                .param("id", UUID.randomUUID())
                .param("donante", donanteId)
                .param("finalidad", finalidad.codigo())
                .param("version", versionAviso)
                .param("otorgado", otorgado)
                .param("ahora", Sql.ts(ahora))
                .update();
    }

    @Override
    public List<Consentimiento> vigentes(UUID donanteId) {
        return jdbc.sql("""
                SELECT DISTINCT ON (finalidad) finalidad, otorgado, version_aviso, creado_en
                FROM consentimiento WHERE donante_id = :donante
                ORDER BY finalidad, creado_en DESC""")
                .param("donante", donanteId)
                .query((rs, n) -> new Consentimiento(Finalidad.desdeCodigo(rs.getString("finalidad")).orElseThrow(),
                        rs.getBoolean("otorgado"), rs.getString("version_aviso"), Sql.instante(rs, "creado_en")))
                .list();
    }
}
