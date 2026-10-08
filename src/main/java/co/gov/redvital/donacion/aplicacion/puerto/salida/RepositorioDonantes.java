package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.Donante;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioDonantes {

    Optional<Donante> buscar(UUID id);

    Optional<Donante> porUsuario(UUID usuarioId);

    Optional<Donante> porDocumentoHash(String documentoHash);

    void insertar(Donante donante, Instant ahora);

    void actualizarContacto(UUID id, String nombre, String correo, String telefono, String municipioRuta, Instant ahora);

    void registrarDonacion(UUID id, LocalDate fecha, LocalDate elegibleDesde, String grupoSanguineo, Instant ahora);

    /** Donantes activos con el aviso de campañas autorizado en ese territorio. */
    List<UUID> conAvisosEnTerritorio(String territorioRuta);
}
