package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.InstitucionesPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionInstitucionesPort;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Proyección {@code institucion_proyeccion}, alimentada por EV-07. */
@Repository
class InstitucionesJdbc implements InstitucionesPort, ProyeccionInstitucionesPort {

    private final JdbcClient jdbc;

    InstitucionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> dentroDe(String ruta) {
        return new HashSet<>(jdbc.sql("""
                        SELECT institucion_id FROM institucion_proyeccion
                        WHERE vigente AND (territorio_ruta = :ruta OR territorio_ruta LIKE :prefijo)""")
                .param("ruta", ruta)
                .param("prefijo", ruta + "/%")
                .query((rs, n) -> Sql.uuid(rs, "institucion_id"))
                .list());
    }

    @Override
    public Optional<String> nombre(UUID institucionId) {
        return jdbc.sql("SELECT nombre FROM institucion_proyeccion WHERE institucion_id = :id")
                .param("id", institucionId)
                .query(String.class)
                .optional();
    }

    @Override
    public void guardar(Institucion i, UUID eventoId, Instant ahora) {
        jdbc.sql("""
                        INSERT INTO institucion_proyeccion (institucion_id, codigo, nombre, tipo, territorio_codigo,
                                                            territorio_ruta, vigente, cambiado_en, evento_id, actualizado_en)
                        VALUES (:id, :codigo, :nombre, :tipo, :territorioCodigo, :ruta, :vigente, :cambiado, :evento, :ahora)
                        ON CONFLICT (institucion_id) DO UPDATE SET
                            codigo = EXCLUDED.codigo, nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo,
                            territorio_codigo = EXCLUDED.territorio_codigo, territorio_ruta = EXCLUDED.territorio_ruta,
                            vigente = EXCLUDED.vigente, cambiado_en = EXCLUDED.cambiado_en,
                            evento_id = EXCLUDED.evento_id, actualizado_en = EXCLUDED.actualizado_en
                        WHERE institucion_proyeccion.cambiado_en <= EXCLUDED.cambiado_en""")
                .param("id", i.id())
                .param("codigo", i.codigo())
                .param("nombre", i.nombre())
                .param("tipo", i.tipo())
                .param("territorioCodigo", i.territorioCodigo())
                .param("ruta", i.territorioRuta())
                .param("vigente", i.vigente())
                .param("cambiado", Sql.ts(i.cambiadoEn()))
                .param("evento", eventoId)
                .param("ahora", Sql.ts(ahora))
                .update();
    }
}
