package co.gov.redvital.donacion.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Conversión de tipos entre Java y PostgreSQL. Las marcas temporales viajan siempre en UTC. */
final class Sql {

    private Sql() {
    }

    static OffsetDateTime ts(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDate.class);
    }

    static UUID uuid(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, UUID.class);
    }

    static Boolean booleano(ResultSet rs, String columna) throws SQLException {
        boolean valor = rs.getBoolean(columna);
        return rs.wasNull() ? null : valor;
    }

    static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }
}
