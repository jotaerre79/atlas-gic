package com.atlas.gic.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DomainDependencyRulesTest {

    @Test
    void domainDoesNotDependOnSpringOrJpa() {
        var classes = new ClassFileImporter().importPackages("com.atlas.gic");

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..")
                .check(classes);
    }

    @Test
    void businessRolesDoNotDependOnSecurityAuthorities() {
        var classes = new ClassFileImporter().importPackages("com.atlas.gic");

        noClasses()
                .that().resideInAPackage("..roles.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..shared.security..")
                .check(classes);
    }
}
