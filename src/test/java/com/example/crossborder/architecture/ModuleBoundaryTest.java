package com.example.crossborder.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {

    private static final List<String> MODULES = List.of(
            "auth", "customer", "wallet", "ledger", "fx", "transfer", "risk", "admin", "audit", "funding"
    );

    @Test
    void modulesDoNotDependOnOtherModulesInternals() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.example.crossborder");

        for (String consumer : MODULES) {
            for (String provider : MODULES) {
                if (consumer.equals(provider)) {
                    continue;
                }

                ArchRule rule = noClasses()
                        .that().resideInAPackage("com.example.crossborder." + consumer + "..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(
                            "com.example.crossborder." + provider + ".application..",
                                "com.example.crossborder." + provider + ".domain..",
                                "com.example.crossborder." + provider + ".infrastructure.."
                        );
                rule.check(classes);
            }
        }
    }

    @Test
    void sharedCodeDoesNotDependOnBusinessModules() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.example.crossborder");

        noClasses()
                .that().resideInAPackage("com.example.crossborder.shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(MODULES.stream()
                        .map(module -> "com.example.crossborder." + module + "..")
                        .toArray(String[]::new))
                .check(classes);
    }
}