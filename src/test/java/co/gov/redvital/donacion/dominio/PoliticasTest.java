package co.gov.redvital.donacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.redvital.donacion.dominio.excepcion.ConfirmacionInvalida;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.modelo.CampaniaPublicada;
import co.gov.redvital.donacion.dominio.modelo.CodigoIntencion;
import co.gov.redvital.donacion.dominio.modelo.Reconocimiento;
import co.gov.redvital.donacion.dominio.servicio.CalculadoraVencimiento;
import co.gov.redvital.donacion.dominio.servicio.OtorgadorReconocimientos;
import co.gov.redvital.donacion.dominio.servicio.PoliticaCampania;
import co.gov.redvital.donacion.dominio.servicio.PoliticaElegibilidad;
import co.gov.redvital.donacion.dominio.servicio.ReglaConfirmacion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PoliticasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 7);

    @Nested
    class Elegibilidad {

        private final PoliticaElegibilidad politica = new PoliticaElegibilidad();
        private final LocalDate adulto = HOY.minusYears(30);

        @Test
        void sin_donacion_previa_es_elegible() {
            var e = politica.evaluar(adulto, null, HOY);
            assertThat(e.elegible()).isTrue();
            assertThat(e.diasRestantes()).isZero();
        }

        @Test
        void el_intervalo_es_de_cincuenta_y_seis_dias() {
            assertThat(politica.evaluar(adulto, HOY.minusDays(55), HOY).elegible()).isFalse();
            assertThat(politica.evaluar(adulto, HOY.minusDays(55), HOY).diasRestantes()).isEqualTo(1);
            assertThat(politica.evaluar(adulto, HOY.minusDays(56), HOY).elegible()).isTrue();
            assertThat(politica.evaluar(adulto, HOY.minusDays(57), HOY).elegible()).isTrue();
        }

        @Test
        void la_edad_va_de_dieciocho_a_sesenta_y_cinco() {
            var menor = politica.evaluar(HOY.minusYears(18).plusDays(1), null, HOY);
            assertThat(menor.elegible()).isFalse();
            assertThat(menor.elegibleDesde()).isEqualTo(HOY.plusDays(1));
            assertThat(politica.evaluar(HOY.minusYears(18), null, HOY).elegible()).isTrue();
            assertThat(politica.evaluar(HOY.minusYears(66).plusDays(1), null, HOY).elegible()).isTrue();
            var mayor = politica.evaluar(HOY.minusYears(66), null, HOY);
            assertThat(mayor.elegible()).isFalse();
            assertThat(mayor.elegibleDesde()).as("nunca, por edad: sin fecha").isNull();
        }
    }

    @Nested
    class Campania {

        private final PoliticaCampania politica = new PoliticaCampania();
        private final UUID institucion = UUID.randomUUID();
        private final Instant inicio = Instant.parse("2026-10-07T13:00:00Z");
        private final Instant fin = Instant.parse("2026-10-07T22:00:00Z");

        private CampaniaPublicada campania(UUID inst) {
            return new CampaniaPublicada(UUID.randomUUID(), inst, "Jornada", "Sede", "05001", "/00/05/05001", inicio, fin,
                    inicio.minusSeconds(86_400), "publicada");
        }

        @Test
        void sin_campania_no_hay_asociacion() {
            assertThat(politica.decidir(null, Optional.empty(), institucion, inicio).campaniaId()).isNull();
        }

        @Test
        void campania_propia_y_vigente_se_confirma() {
            var c = campania(institucion);
            var d = politica.decidir(c.campaniaId(), Optional.of(c), institucion, inicio.plusSeconds(60));
            assertThat(d.campaniaId()).isEqualTo(c.campaniaId());
            assertThat(d.confirmada()).isTrue();
        }

        @Test
        void campania_desconocida_o_ajena_degrada_sin_bloquear() {
            var ajena = campania(UUID.randomUUID());
            assertThat(politica.decidir(ajena.campaniaId(), Optional.of(ajena), institucion, inicio).confirmada()).isFalse();
            assertThat(politica.decidir(UUID.randomUUID(), Optional.empty(), institucion, inicio).campaniaId()).isNull();
        }

        @Test
        void campania_propia_fuera_de_fechas_es_un_conflicto() {
            var c = campania(institucion);
            assertThatThrownBy(() -> politica.decidir(c.campaniaId(), Optional.of(c), institucion, fin.plusSeconds(1)))
                    .isInstanceOf(Conflicto.class);
        }
    }

    @Test
    void el_vencimiento_suma_la_vida_util_en_la_zona_horaria_de_colombia() {
        var calculadora = new CalculadoraVencimiento(ZoneId.of("America/Bogota"));
        // 02:00 UTC del 8 de octubre es todavía 7 de octubre en Bogotá.
        assertThat(calculadora.vencimiento(Instant.parse("2026-10-08T02:00:00Z"), 5)).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void la_confirmacion_exige_el_identificador_exacto() {
        var regla = new ReglaConfirmacion();
        UUID id = UUID.randomUUID();
        regla.verificar(id, " " + id.toString().toUpperCase() + " ");
        assertThatThrownBy(() -> regla.verificar(id, "si")).isInstanceOf(ConfirmacionInvalida.class);
        assertThatThrownBy(() -> regla.verificar(id, null)).isInstanceOf(ConfirmacionInvalida.class);
    }

    @Test
    void el_codigo_de_intencion_usa_doce_simbolos_sin_ambiguedad() {
        var azar = new SplittableRandom(7);
        for (int i = 0; i < 500; i++) {
            String codigo = CodigoIntencion.generar(azar);
            assertThat(codigo).hasSize(12).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{12}");
        }
        assertThat(CodigoIntencion.normalizar("k7p3-mxr2 9qht")).contains("K7P3MXR29QHT");
        assertThat(CodigoIntencion.normalizar("K7P3MXR29QH0")).as("el cero es ambiguo").isEmpty();
    }

    @Test
    void los_reconocimientos_se_otorgan_una_sola_vez_al_alcanzar_su_criterio() {
        var catalogo = List.of(
                new Reconocimiento(UUID.randomUUID(), "primera_donacion", "Primera", "d", 1, 1),
                new Reconocimiento(UUID.randomUUID(), "tres_donaciones", "Tres", "d", 3, 2));
        var otorgador = new OtorgadorReconocimientos();
        assertThat(otorgador.nuevos(1, catalogo, Set.of())).extracting(Reconocimiento::codigo).containsExactly("primera_donacion");
        assertThat(otorgador.nuevos(3, catalogo, Set.of("primera_donacion"))).extracting(Reconocimiento::codigo)
                .containsExactly("tres_donaciones");
        assertThat(otorgador.nuevos(4, catalogo, Set.of("primera_donacion", "tres_donaciones"))).isEmpty();
    }
}
