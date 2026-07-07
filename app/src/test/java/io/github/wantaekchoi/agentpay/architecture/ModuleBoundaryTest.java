package io.github.wantaekchoi.agentpay.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.wantaekchoi.agentpay");

    @Test
    void identityDomainDoesNotDependOnWeb3j() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAPackage("org.web3j..");
        rule.check(classes);
    }

    @Test
    void identityDoesNotDependOnWebLayerFromDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAPackage("..identity.web..");
        rule.check(classes);
    }

    @Test
    void portsAreInterfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage("..identity.port..")
                .should().beInterfaces();
        rule.check(classes);
    }
}
