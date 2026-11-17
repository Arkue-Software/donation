package co.gov.redvital.donacion.aplicacion.casodeuso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regla del nombre de una persona: el nombre sale en el saludo de los correos (EV-02). */
class ValidacionesTest {

    @Test
    void acepta_nombres_con_tildes_guiones_apostrofos_y_abreviaturas() {
        for (String nombre : List.of("Laura Pérez", "María-José O'Neill", "Ana Ma. Peña", "J. Pérez Jr.")) {
            assertThat(Validaciones.nombrePersona(" " + nombre + " ", "nombre", 120)).isEqualTo(nombre);
        }
    }

    @Test
    void rechaza_enlaces_numeros_y_marcado() {
        for (String nombre : List.of("http://sitio.co", "Entra a sitio.co", "Ana 2026", "<b>Ana</b>", "Ana\nRojas")) {
            assertThatThrownBy(() -> Validaciones.nombrePersona(nombre, "nombre", 120))
                    .isInstanceOf(DatosInvalidos.class)
                    .hasMessageContaining("nombre");
        }
    }
}
