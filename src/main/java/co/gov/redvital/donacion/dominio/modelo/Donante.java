package co.gov.redvital.donacion.dominio.modelo;

import java.time.LocalDate;
import java.util.UUID;

/** Perfil del donante registrado (U2). El documento solo existe como resumen HMAC. */
public record Donante(
        UUID id,
        UUID usuarioId,
        String documentoHash,
        String nombre,
        String correo,
        String telefono,
        LocalDate fechaNacimiento,
        String municipioRuta,
        String grupoSanguineo,
        LocalDate fechaUltimaDonacion,
        LocalDate elegibleDesde,
        boolean activo) {
}
