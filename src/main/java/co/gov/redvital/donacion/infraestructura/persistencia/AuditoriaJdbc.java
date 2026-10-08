package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.AuditoriaPort;
import co.gov.redvital.donacion.dominio.modelo.RegistroAuditoria;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuditoriaJdbc implements AuditoriaPort {

    private final JdbcClient jdbc;

    AuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroAuditoria r) {
        jdbc.sql("""
                INSERT INTO registro_auditoria (id, actor_tipo, actor_id, rol, jurisdiccion_solicitada, operacion,
                                                recurso_tipo, recurso_id, resultado, correlacion_id, origen, ocurrido_en)
                VALUES (:id, :actorTipo, :actorId, :rol, :jurisdiccion, :operacion, :recursoTipo, :recursoId,
                        :resultado, :correlacion, :origen, :ocurrido)""")
                .param("id", UUID.randomUUID())
                .param("actorTipo", r.actorTipo())
                .param("actorId", recortar(r.actorId(), 64))
                .param("rol", recortar(r.rol(), 40))
                .param("jurisdiccion", recortar(r.jurisdiccionSolicitada(), 80))
                .param("operacion", recortar(r.operacion(), 80))
                .param("recursoTipo", recortar(r.recursoTipo(), 40))
                .param("recursoId", r.recursoId())
                .param("resultado", r.permitido() ? "permitido" : "denegado")
                .param("correlacion", r.correlacionId())
                .param("origen", recortar(r.origen(), 45))
                .param("ocurrido", Sql.ts(r.ocurridoEn()))
                .update();
    }

    private static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
