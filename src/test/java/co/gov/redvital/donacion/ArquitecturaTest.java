package co.gov.redvital.donacion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regla de dependencias de la arquitectura hexagonal: el dominio no depende
 * de nada externo y la aplicación solo del dominio. Los adaptadores dependen
 * hacia dentro, nunca al revés.
 */
@AnalyzeClasses(packages = "co.gov.redvital.donacion", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule dominio_puro = noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..",
                    "com.fasterxml..", "org.apache.kafka..", "java.sql..", "..aplicacion..", "..infraestructura..",
                    "..web..");

    @ArchTest
    static final ArchRule aplicacion_sin_marcos = noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..",
                    "org.apache.kafka..", "java.sql..", "..infraestructura..", "..web..");

    @ArchTest
    static final ArchRule solo_la_mensajeria_usa_kafka = noClasses()
            .that().resideOutsideOfPackage("..infraestructura.mensajeria..")
            .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..");
}
