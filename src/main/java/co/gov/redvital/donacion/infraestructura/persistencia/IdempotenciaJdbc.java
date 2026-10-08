package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.IdempotenciaPort;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class IdempotenciaJdbc implements IdempotenciaPort {

    private final JdbcClient jdbc;

    IdempotenciaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reservar(Operacion o) {
        return jdbc.sql("""
                INSERT INTO operacion_idempotente (sujeto, clave, operacion, huella_peticion, recurso_id, codigo_estado,
                                                   creado_en, expira_en)
                VALUES (:sujeto, :clave, :operacion, :huella, :recurso, :codigo, :creado, :expira)
                ON CONFLICT (sujeto, clave) DO NOTHING""")
                .param("sujeto", o.sujeto())
                .param("clave", o.clave())
                .param("operacion", o.operacion())
                .param("huella", o.huella())
                .param("recurso", o.recursoId())
                .param("codigo", o.codigoEstado())
                .param("creado", Sql.ts(o.creadoEn()))
                .param("expira", Sql.ts(o.expiraEn()))
                .update() == 1;
    }

    @Override
    public Optional<Operacion> buscar(String sujeto, String clave) {
        return jdbc.sql("""
                SELECT sujeto, clave, operacion, huella_peticion, recurso_id, codigo_estado, creado_en, expira_en
                FROM operacion_idempotente WHERE sujeto = :sujeto AND clave = :clave""")
                .param("sujeto", sujeto)
                .param("clave", clave)
                .query((rs, n) -> new Operacion(rs.getString("sujeto"), rs.getString("clave"), rs.getString("operacion"),
                        rs.getString("huella_peticion"), Sql.uuid(rs, "recurso_id"), rs.getInt("codigo_estado"),
                        Sql.instante(rs, "creado_en"), Sql.instante(rs, "expira_en")))
                .optional();
    }

    @Override
    public int depurar(Instant ahora) {
        return jdbc.sql("DELETE FROM operacion_idempotente WHERE expira_en < :ahora")
                .param("ahora", Sql.ts(ahora)).update();
    }
}
