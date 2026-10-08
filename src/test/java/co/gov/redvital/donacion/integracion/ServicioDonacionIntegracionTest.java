package co.gov.redvital.donacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.gov.redvital.donacion.aplicacion.casodeuso.EvaluarAlertas;
import co.gov.redvital.donacion.aplicacion.casodeuso.ProcesarVencimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CuentasIdentidadPort;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recorridos completos del servicio contra PostgreSQL y Kafka reales. Cada
 * prueba usa su propia institución para no depender del orden de ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServicioDonacionIntegracionTest {

    private static final String CAMPANIA_PUBLICADA = "redvital.campanias.campaign-published.v1";
    private static final String DONACION_COMPLETADA = "redvital.donacion.donation-completed.v1";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", EntornoIntegracion::urlJdbc);
        r.add("spring.datasource.username", () -> "donacion_servicio");
        r.add("spring.datasource.password", () -> EntornoIntegracion.CLAVE_SERVICIO);
        r.add("spring.kafka.bootstrap-servers", EntornoIntegracion.KAFKA::getBootstrapServers);
        r.add("spring.kafka.security.protocol", () -> "PLAINTEXT");
        r.add("redvital.documento.clave-hmac", () -> "clave-hmac-solo-para-pruebas-de-integracion");
        r.add("redvital.jwt.jwks-uri", () -> "http://127.0.0.1:9/no-se-usa");
        r.add("redvital.identidad.url", () -> "http://127.0.0.1:9");
        r.add("redvital.identidad.secreto", () -> "no-se-usa");
    }

    @TestConfiguration
    static class LlavesDePrueba {

        /** Mismas validaciones que producción, con la llave pública generada para las pruebas. */
        @Bean
        @Primary
        JwtDecoder decodificadorDePruebas() {
            NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey(EntornoIntegracion.llavePublica()).build();
            decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(EntornoIntegracion.EMISOR)));
            return decodificador;
        }
    }

    @MockitoBean
    CuentasIdentidadPort cuentas;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ProcesarVencimientos vencimientos;

    @Autowired
    EvaluarAlertas alertas;

    // ------------------------------------------------------------------ M1

    @Test
    void registro_anonimo_es_idempotente_y_la_consulta_publica_no_revela_datos() throws Exception {
        String cuerpo = "{\"grupo_sanguineo\":\"O-\",\"municipio_ruta\":\"/00/05/05001\"}";
        JsonNode primero = cuerpo(enviar(post("/v1/intenciones"), null, "intencion-1", cuerpo), 201);
        String codigo = primero.path("codigo").asString();
        assertThat(codigo).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{12}");

        JsonNode reenvio = cuerpo(enviar(post("/v1/intenciones"), null, "intencion-1", cuerpo), 201);
        assertThat(reenvio.path("codigo").asString()).as("un reenvío devuelve el mismo código").isEqualTo(codigo);

        problema(enviar(post("/v1/intenciones"), null, "intencion-1", "{\"grupo_sanguineo\":\"A+\"}"), 422,
                "clave-idempotencia-reutilizada");

        JsonNode publica = cuerpo(enviar(get("/v1/intenciones/" + codigo.toLowerCase()), null, null, null), 200);
        assertThat(publica.path("estado").asString()).isEqualTo("pendiente");
        assertThat(publica.path("grupo_sanguineo").isNull()).as("sin token no se ve el grupo").isTrue();

        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador",
                "institucion:" + UUID.randomUUID());
        JsonNode delOperador = cuerpo(enviar(get("/v1/intenciones/" + codigo), operador, null, null), 200);
        assertThat(delOperador.path("grupo_sanguineo").asString()).isEqualTo("O-");

        problema(enviar(get("/v1/intenciones/ZZZZZZZZZZZZ"), null, null, null), 404, "no-encontrado");
    }

    @Test
    void crear_cuenta_de_donante_y_consultar_el_perfil_propio() throws Exception {
        UUID usuario = UUID.randomUUID();
        when(cuentas.crearCuentaDonante(anyString(), anyString(), anyString(), anyString())).thenReturn(usuario);
        String documento = documentoNuevo();

        JsonNode creado = cuerpo(enviar(post("/v1/donantes"), null, "registro-" + documento,
                registroDonante(documento, "laura" + documento + "@correo.test", true)), 201);
        assertThat(creado.path("usuario_id").asString()).isEqualTo(usuario.toString());
        assertThat(creado.has("documento")).as("el documento nunca se devuelve").isFalse();

        String donante = EntornoIntegracion.token(usuario.toString(), "donante", "");
        JsonNode perfil = cuerpo(enviar(get("/v1/donantes/me"), donante, null, null), 200);
        assertThat(perfil.path("nombre").asString()).isEqualTo("Laura Pérez");
        assertThat(perfil.path("grupo_sanguineo").isNull()).as("el grupo se tipifica en la primera donación").isTrue();

        JsonNode elegibilidad = cuerpo(enviar(get("/v1/donantes/me/elegibilidad"), donante, null, null), 200);
        assertThat(elegibilidad.path("elegible").asBoolean()).isTrue();

        JsonNode consentimientos = cuerpo(enviar(get("/v1/donantes/me/consentimientos"), donante, null, null), 200);
        assertThat(consentimientos).hasSize(2);

        JsonNode revocado = cuerpo(enviar(post("/v1/donantes/me/consentimientos"), donante, null,
                "{\"finalidad\":\"avisos_campanas\",\"otorgado\":false,\"version_aviso\":\"AV-2026.1\"}"), 200);
        assertThat(buscar(revocado, "finalidad", "avisos_campanas").path("otorgado").asBoolean()).isFalse();

        problema(enviar(post("/v1/donantes"), null, "registro-repetido-" + documento,
                registroDonante(documento, "otra" + documento + "@correo.test", true)), 409, "conflicto");
        problema(enviar(post("/v1/donantes"), null, "sin-consentimiento-" + documento,
                registroDonante(documentoNuevo(), "x" + documento + "@correo.test", false)), 422, "consentimiento-requerido");
    }

    // ------------------------------------------------------------------ M3 y eventos

    @Test
    void ciclo_de_vida_completo_con_campania_por_kafka_y_evento_de_donacion() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);
        String admin = EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco", "institucion:" + institucion);

        // La campaña llega solo por Kafka (EV-03): Donación nunca llama a Campañas.
        UUID campania = UUID.randomUUID();
        publicarCampania(campania, institucion, UUID.randomUUID(), "{"
                + "\"campania_id\":\"" + campania + "\",\"institucion_id\":\"" + institucion + "\","
                + "\"nombre\":\"Jornada de prueba\",\"sede\":\"Parque\",\"territorio_codigo\":\"05001\","
                + "\"territorio_ruta\":\"/00/05/05001\",\"inicia_en\":\"" + Instant.now().minusSeconds(3600) + "\","
                + "\"termina_en\":\"" + Instant.now().plusSeconds(8 * 3600) + "\","
                + "\"publicada_en\":\"" + Instant.now().minusSeconds(86_400) + "\",\"campo_futuro\":1}");
        esperar(() -> existeFila("SELECT 1 FROM campania_publicada WHERE campania_id = '" + campania + "'"));

        UUID usuario = UUID.randomUUID();
        when(cuentas.crearCuentaDonante(anyString(), anyString(), anyString(), anyString())).thenReturn(usuario);
        String documento = documentoNuevo();
        cuerpo(enviar(post("/v1/donantes"), null, "r-" + documento,
                registroDonante(documento, "d" + documento + "@correo.test", true)), 201);

        JsonNode encontrado = cuerpo(enviar(post("/v1/donantes/busqueda"), operador, null,
                "{\"documento\":\"" + documento + "\"}"), 200);
        assertThat(encontrado.path("elegibilidad").path("elegible").asBoolean()).isTrue();
        String donanteId = encontrado.path("id").asString();

        String registro = "{\"donante_id\":\"" + donanteId + "\",\"grupo_sanguineo\":\"A+\",\"campania_id\":\""
                + campania + "\",\"componentes\":[{\"componente\":\"globulos_rojos\",\"volumen_ml\":450},"
                + "{\"componente\":\"plasma\"}]}";
        JsonNode donacion = cuerpo(enviar(post("/v1/donaciones"), operador, "donacion-" + donanteId, registro), 201);
        String donacionId = donacion.path("id").asString();
        assertThat(donacion.path("campania_confirmada").asBoolean()).isTrue();
        assertThat(donacion.path("institucion_id").asString()).isEqualTo(institucion.toString());
        assertThat(donacion.path("unidades")).hasSize(2);
        donacion.path("unidades").forEach(u -> assertThat(u.path("estado").asString()).isEqualTo("captada"));

        JsonNode reenvio = cuerpo(enviar(post("/v1/donaciones"), operador, "donacion-" + donanteId, registro), 201);
        assertThat(reenvio.path("id").asString()).as("el reenvío no crea una segunda donación").isEqualTo(donacionId);

        problema(enviar(post("/v1/donaciones"), operador, "otra-" + donanteId, registro), 422, "donante-no-elegible");

        String donante = EntornoIntegracion.token(usuario.toString(), "donante", "");
        assertThat(cuerpo(enviar(get("/v1/donantes/me/donaciones"), donante, null, null), 200)).hasSize(1);
        JsonNode reconocimientos = cuerpo(enviar(get("/v1/donantes/me/reconocimientos"), donante, null, null), 200);
        assertThat(buscar(reconocimientos, "codigo", "primera_donacion").path("otorgado_en").isNull()).isFalse();

        // EV-01 sale por la bandeja hacia Kafka, sin datos personales.
        ConsumerRecord<String, String> evento = esperarMensaje(DONACION_COMPLETADA, donacionId);
        assertThat(cabecera(evento, "evento-tipo")).isEqualTo("donation.completed");
        assertThat(cabecera(evento, "productor")).isEqualTo("donacion");
        JsonNode carga = json.readTree(evento.value());
        assertThat(carga.path("campania_id").asString()).isEqualTo(campania.toString());
        assertThat(carga.path("unidades_generadas").asInt()).isEqualTo(2);
        assertThat(evento.value()).doesNotContain(documento).doesNotContain("Laura");

        JsonNode fraccionada = cuerpo(enviar(post("/v1/donaciones/" + donacionId + "/fraccionamiento"), operador,
                "fracc-" + donacionId, null), 200);
        fraccionada.path("unidades").forEach(u -> assertThat(u.path("estado").asString()).isEqualTo("fraccionada"));

        String globulos = idDe(fraccionada.path("unidades"), "globulos_rojos");
        String plasma = idDe(fraccionada.path("unidades"), "plasma");

        // Glóbulos rojos: apta, reservada y despachada con confirmación.
        transicion(post("/v1/unidades/" + globulos + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        transicion(post("/v1/unidades/" + globulos + "/tamizaje"), operador, null, "{\"apta\":true}", "disponible");
        JsonNode inventario = cuerpo(enviar(get("/v1/inventario"), admin, null, null), 200);
        assertThat(buscar(inventario, "componente", "globulos_rojos").path("unidades_disponibles").asLong()).isEqualTo(1);
        transicion(post("/v1/unidades/" + globulos + "/reserva"), operador, null, null, "reservada");
        problema(enviar(post("/v1/unidades/" + globulos + "/despacho"), operador, "desp-" + globulos,
                "{\"confirmacion\":\"si\"}"), 422, "confirmacion-invalida");
        transicion(post("/v1/unidades/" + globulos + "/despacho"), operador, "desp2-" + globulos,
                "{\"confirmacion\":\"" + globulos + "\"}", "despachada");

        // Plasma: el tamizaje no admite ningún campo de motivo (EC-02).
        transicion(post("/v1/unidades/" + plasma + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        problema(enviar(post("/v1/unidades/" + plasma + "/tamizaje"), operador, null,
                "{\"apta\":false,\"motivo\":\"cualquiera\"}"), 400, "validacion");
        transicion(post("/v1/unidades/" + plasma + "/tamizaje"), operador, null, "{\"apta\":false}", "no_apta");
        transicion(post("/v1/unidades/" + plasma + "/disposicion-final"), operador, "disp-" + plasma,
                "{\"confirmacion\":\"" + plasma + "\"}", "desechada");

        // El ciclo no retrocede, y el intento queda en la bitácora.
        problema(enviar(post("/v1/unidades/" + plasma + "/ingreso-tamizaje"), operador, null, null), 409,
                "conflicto-de-estado");
        assertThat(existeFila("SELECT 1 FROM registro_auditoria WHERE recurso_id = '" + plasma
                + "' AND resultado = 'denegado' AND operacion = 'iniciar_tamizaje'")).isTrue();

        JsonNode eventos = cuerpo(enviar(get("/v1/unidades/" + plasma + "/eventos"), admin, null, null), 200);
        assertThat(eventos).extracting(e -> e.path("estado_nuevo").asString())
                .containsExactly("captada", "fraccionada", "en_tamizaje", "no_apta", "desechada");
        JsonNode unidad = cuerpo(enviar(get("/v1/unidades/" + plasma), admin, null, null), 200);
        assertThat(unidad.has("motivo")).isFalse();
        assertThat(unidad.path("apta").asBoolean()).isFalse();
    }

    @Test
    void controles_de_acceso_por_rol_y_por_jurisdiccion() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);

        problema(enviar(get("/v1/unidades"), null, null, null), 401, "sesion-invalida");
        problema(enviar(get("/v1/unidades"), "token.alterado.x", null, null), 401, "sesion-invalida");
        problema(enviar(get("/v1/unidades"), EntornoIntegracion.token(UUID.randomUUID().toString(), "coordinador",
                "territorio:/00/05"), null, null), 403, "operacion-no-permitida");
        problema(enviar(get("/v1/unidades"), EntornoIntegracion.token(UUID.randomUUID().toString(), "donante", ""),
                null, null), 403, "operacion-no-permitida");
        problema(enviar(post("/v1/donaciones"), EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco",
                "institucion:" + institucion), "x", "{\"grupo_sanguineo\":\"O+\",\"componentes\":[{\"componente\":\"plasma\"}]}"),
                403, "operacion-no-permitida");
        problema(enviar(get("/v1/inventario?institucion_id=" + UUID.randomUUID()), operador, null, null), 403,
                "fuera-de-jurisdiccion");
        problema(enviar(get("/v1/unidades/" + UUID.randomUUID()), operador, null, null), 404, "no-encontrado");
        problema(enviar(get("/internal/v1/cualquier-cosa"), null, null, null), 404, "no-encontrado");
        assertThat(existeFila("SELECT 1 FROM registro_auditoria WHERE resultado = 'denegado' AND rol = 'coordinador'"))
                .as("las denegaciones de rol quedan auditadas").isTrue();
    }

    // ------------------------------------------------------------------ M4 y procesos

    @Test
    void vencimiento_automatico_y_alertas_de_escasez() throws Exception {
        UUID institucion = UUID.randomUUID();
        String operador = EntornoIntegracion.token(UUID.randomUUID().toString(), "operador", "institucion:" + institucion);
        String admin = EntornoIntegracion.token(UUID.randomUUID().toString(), "admin_banco", "institucion:" + institucion);

        JsonNode donacion = cuerpo(enviar(post("/v1/donaciones"), operador, "anon-" + institucion,
                "{\"grupo_sanguineo\":\"O+\",\"componentes\":[{\"componente\":\"plaquetas\"}]}"), 201);
        assertThat(donacion.path("donante_id").isNull()).as("donación sin identificar").isTrue();
        assertThat(donacion.path("campania_confirmada").asBoolean()).isFalse();
        String donacionId = donacion.path("id").asString();
        String plaquetas = donacion.path("unidades").get(0).path("id").asString();
        enviar(post("/v1/donaciones/" + donacionId + "/fraccionamiento"), operador, "f-" + donacionId, null);
        transicion(post("/v1/unidades/" + plaquetas + "/ingreso-tamizaje"), operador, null, null, "en_tamizaje");
        transicion(post("/v1/unidades/" + plaquetas + "/tamizaje"), operador, null, "{\"apta\":true}", "disponible");

        cuerpo(enviar(put("/v1/inventario/umbrales"), admin, null, "{\"umbrales\":[{\"componente\":\"plaquetas\","
                + "\"grupo_sanguineo\":\"O+\",\"minimo_unidades\":5,\"dias_previos_vencimiento\":7}]}"), 200);
        problema(enviar(put("/v1/inventario/umbrales"), operador, null, "{\"umbrales\":[]}"), 403,
                "operacion-no-permitida");

        alertas.ejecutar();
        JsonNode abiertas = cuerpo(enviar(get("/v1/alertas?estado=abierta"), operador, null, null), 200);
        assertThat(abiertas).extracting(a -> a.path("tipo").asString())
                .contains("escasez", "vencimiento_proximo");

        try (Connection c = EntornoIntegracion.superusuario()) {
            c.createStatement().execute("UPDATE unidad SET fecha_vencimiento = current_date - 1 WHERE id = '"
                    + plaquetas + "'");
        }
        int retiradas = vencimientos.ejecutar();
        assertThat(retiradas).isGreaterThanOrEqualTo(1);
        JsonNode vencida = cuerpo(enviar(get("/v1/unidades/" + plaquetas), operador, null, null), 200);
        assertThat(vencida.path("estado").asString()).isEqualTo("vencida");
        JsonNode eventos = cuerpo(enviar(get("/v1/unidades/" + plaquetas + "/eventos"), operador, null, null), 200);
        JsonNode ultimo = eventos.get(eventos.size() - 1);
        assertThat(ultimo.path("actor_tipo").asString()).as("el vencimiento es del sistema").isEqualTo("sistema");

        String escasez = buscar(abiertas, "tipo", "escasez").path("id").asString();
        JsonNode atendida = cuerpo(enviar(post("/v1/alertas/" + escasez + "/atencion"), admin, null, null), 200);
        assertThat(atendida.path("estado").asString()).isEqualTo("atendida");
        problema(enviar(post("/v1/alertas/" + escasez + "/atencion"), admin, null, null), 409, "conflicto");
    }

    @Test
    void el_rol_de_servicio_no_puede_reescribir_la_historia() throws Exception {
        try (Connection c = EntornoIntegracion.servicio()) {
            assertThatThrownBy(() -> c.createStatement().execute("UPDATE donacion SET campania_id = NULL"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> c.createStatement().execute("UPDATE unidad SET fecha_vencimiento = current_date"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> c.createStatement().execute("DELETE FROM evento_unidad"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> c.createStatement().execute("DELETE FROM registro_auditoria"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void un_evento_que_no_cumple_el_contrato_va_al_tema_de_mensajes_muertos() throws Exception {
        UUID eventoId = UUID.randomUUID();
        publicarCampania(UUID.randomUUID(), UUID.randomUUID(), eventoId, "{esto no es json");
        ConsumerRecord<String, String> muerto = esperarMensajePorCabecera(CAMPANIA_PUBLICADA + ".donacion.dlt",
                "evento-id", eventoId.toString());
        assertThat(muerto.value()).isEqualTo("{esto no es json");
    }

    // ------------------------------------------------------------------ utilidades

    private MvcResult enviar(MockHttpServletRequestBuilder peticion, String token, String clave, String cuerpo)
            throws Exception {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (clave != null) {
            peticion.header("Idempotency-Key", clave);
        }
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion.header("X-Forwarded-For", "10.0.0.7")).andReturn();
    }

    private JsonNode cuerpo(MvcResult resultado, int estado) throws Exception {
        String contenido = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(resultado.getResponse().getStatus()).as(contenido).isEqualTo(estado);
        return json.readTree(contenido);
    }

    private void problema(MvcResult resultado, int estado, String tipo) throws Exception {
        JsonNode problema = cuerpo(resultado, estado);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(problema.path("tipo").asString()).isEqualTo(tipo);
        assertThat(problema.path("correlacion_id").asString()).isNotBlank();
    }

    private void transicion(MockHttpServletRequestBuilder peticion, String token, String clave, String cuerpo,
                            String estadoEsperado) throws Exception {
        JsonNode resultado = cuerpo(enviar(peticion, token, clave, cuerpo), 200);
        assertThat(resultado.path("estado_nuevo").asString()).isEqualTo(estadoEsperado);
    }

    private static JsonNode buscar(JsonNode lista, String campo, String valor) {
        for (JsonNode elemento : lista) {
            if (valor.equals(elemento.path(campo).asString())) {
                return elemento;
            }
        }
        throw new AssertionError("No hay elemento con " + campo + "=" + valor + " en " + lista);
    }

    private static String idDe(JsonNode unidades, String componente) {
        return buscar(unidades, "componente", componente).path("id").asString();
    }

    private static String registroDonante(String documento, String correo, boolean autoriza) {
        return "{\"documento\":\"" + documento + "\",\"nombre\":\"Laura Pérez\",\"fecha_nacimiento\":\""
                + LocalDate.now().minusYears(30) + "\",\"correo_acceso\":\"" + correo + "\","
                + "\"credencial\":\"ClaveSegura2026\",\"correo\":null,\"telefono\":\"3001234567\","
                + "\"municipio_ruta\":\"/00/05/05001\",\"autoriza_tratamiento_datos\":" + autoriza + ","
                + "\"autoriza_avisos_campanas\":true,\"version_aviso\":\"AV-2026.1\"}";
    }

    private static String documentoNuevo() {
        return String.valueOf(1_000_000_000L + (long) (Math.random() * 8_000_000_000L));
    }

    private static boolean existeFila(String consulta) {
        try (Connection c = EntornoIntegracion.superusuario()) {
            return c.createStatement().executeQuery(consulta).next();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(limite)) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("La condición no se cumplió en 30 segundos");
    }

    private static void publicarCampania(UUID campania, UUID institucion, UUID eventoId, String carga) {
        try (var productor = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EntornoIntegracion.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            var registro = new ProducerRecord<>(CAMPANIA_PUBLICADA, campania.toString(), carga);
            registro.headers().add("evento-id", eventoId.toString().getBytes(StandardCharsets.UTF_8));
            registro.headers().add("evento-tipo", "campaign.published".getBytes(StandardCharsets.UTF_8));
            registro.headers().add("correlacion-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            registro.headers().add("productor", "campanias".getBytes(StandardCharsets.UTF_8));
            productor.send(registro).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ConsumerRecord<String, String> esperarMensaje(String tema, String clave) {
        return esperarEn(tema, r -> clave.equals(r.key()));
    }

    private static ConsumerRecord<String, String> esperarMensajePorCabecera(String tema, String cabecera, String valor) {
        return esperarEn(tema, r -> valor.equals(cabecera(r, cabecera)));
    }

    private static ConsumerRecord<String, String> esperarEn(String tema,
                                                            java.util.function.Predicate<ConsumerRecord<String, String>> filtro) {
        try (var consumidor = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EntornoIntegracion.KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumidor.subscribe(List.of(tema));
            Instant limite = Instant.now().plus(Duration.ofSeconds(45));
            while (Instant.now().isBefore(limite)) {
                for (ConsumerRecord<String, String> r : consumidor.poll(Duration.ofMillis(500))) {
                    if (filtro.test(r)) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("No llegó el mensaje esperado al tema " + tema);
    }

    private static String cabecera(ConsumerRecord<String, String> r, String nombre) {
        var h = r.headers().lastHeader(nombre);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
