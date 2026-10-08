package co.gov.redvital.donacion.integracion;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.flywaydb.core.Flyway;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Infraestructura real para las pruebas de integración, levantada una sola
 * vez por ejecución: PostgreSQL 16 con los roles y las migraciones del
 * repositorio de bases (las mismas que aplica el despliegue) y Kafka 4.3.1 con
 * los temas creados de antemano, como lo hace el trabajo de inicialización.
 *
 * El servicio se conecta como donacion_servicio, de modo que las pruebas
 * verifican también los permisos por columna del esquema.
 */
final class EntornoIntegracion {

    static final String CLAVE_PROPIETARIO = "propietario-prueba";
    static final String CLAVE_SERVICIO = "servicio-prueba";
    static final String EMISOR = "https://identidad.redvital.local";
    static final String AUDIENCIA = "redvital";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16").withDatabaseName("db_donacion");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");
    static final KeyPair LLAVES = generarLlaves();

    static {
        POSTGRES.start();
        KAFKA.start();
        prepararBase();
        crearTemas();
    }

    private EntornoIntegracion() {
    }

    static String urlJdbc() {
        return POSTGRES.getJdbcUrl();
    }

    static RSAPublicKey llavePublica() {
        return (RSAPublicKey) LLAVES.getPublic();
    }

    /** Conexión como superusuario, solo para preparar datos que el servicio no puede escribir. */
    static Connection superusuario() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    static Connection servicio() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "donacion_servicio", CLAVE_SERVICIO);
    }

    static String token(String sub, String rol, String jurisdiccion) {
        try {
            Instant ahora = Instant.now();
            var reivindicaciones = new JWTClaimsSet.Builder()
                    .subject(sub).issuer(EMISOR).audience(AUDIENCIA)
                    .issueTime(Date.from(ahora)).expirationTime(Date.from(ahora.plusSeconds(900)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", rol).claim("jurisdiction", jurisdiccion)
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("prueba").build(), reivindicaciones);
            jwt.sign(new RSASSASigner(LLAVES.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void prepararBase() {
        try {
            Path dir = Path.of(System.getProperty("redvital.migraciones.dir",
                    "../../../BDs_RedVital/databases/donacion")).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir.resolve("migraciones"))) {
                throw new IllegalStateException("No se encontraron las migraciones en " + dir);
            }
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(dir.resolve("00_roles.sql")), "/tmp/00_roles.sql");
            var resultado = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", "db_donacion",
                    "-v", "password_propietario=" + CLAVE_PROPIETARIO, "-v", "password_servicio=" + CLAVE_SERVICIO,
                    "-f", "/tmp/00_roles.sql");
            if (resultado.getExitCode() != 0) {
                throw new IllegalStateException("Falló 00_roles.sql: " + resultado.getStderr());
            }
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), "donacion_propietario", CLAVE_PROPIETARIO)
                    .locations("filesystem:" + dir.resolve("migraciones"))
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible preparar la base de pruebas", e);
        }
    }

    private static void crearTemas() {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("redvital.donacion.donation-completed.v1", 3, (short) 1),
                    new NewTopic("redvital.donacion.notice-requested.v1", 3, (short) 1),
                    new NewTopic("redvital.campanias.campaign-published.v1", 3, (short) 1),
                    new NewTopic("redvital.campanias.campaign-published.v1.donacion.dlt", 1, (short) 1)))
                    .all().get();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible crear los temas de prueba", e);
        }
    }

    private static KeyPair generarLlaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
