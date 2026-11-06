import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    base
    alias(libs.plugins.boot.application) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs) apply false
}

spotless {
    format("buildFiles") {
        target("*.gradle.kts", "gradle/*.toml", "buildSrc/**/*.gradle.kts", "payguard-*/build.gradle.kts", "payguard-*/settings.gradle.kts")
        targetExclude("buildSrc/build/**", "buildSrc/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "testRuntimeOnly"(rootProject.libs.org.junit.platform.junit.platform.launcher)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    pluginManager.withPlugin(rootProject.libs.plugins.spotless.get().pluginId) {
        extensions.configure<SpotlessExtension> {
            java {
                target("src/**/*.java")
                targetExclude("src/**/generated/**")
                googleJavaFormat(rootProject.libs.versions.google.java.format.get()).aosp()
                formatAnnotations()
            }
        }
    }

    pluginManager.withPlugin("checkstyle") {
        extensions.configure<CheckstyleExtension> {
            toolVersion = rootProject.libs.versions.checkstyle.get()
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        }
        tasks.withType<Checkstyle>().configureEach {
            exclude { "/generated/" in it.file.invariantSeparatorsPath }
        }
    }

    pluginManager.withPlugin("pmd") {
        extensions.configure<PmdExtension> {
            toolVersion = rootProject.libs.versions.pmd.get()
            ruleSets = listOf("category/java/errorprone.xml")
        }
        tasks.withType<Pmd>().configureEach {
            exclude { "/generated/" in it.file.invariantSeparatorsPath }
        }
    }

    pluginManager.withPlugin(rootProject.libs.plugins.spotbugs.get().pluginId) {
        extensions.configure<SpotBugsExtension> {
            toolVersion.set(rootProject.libs.versions.spotbugs.asProvider())
            excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        }
        tasks.withType<SpotBugsTask>().configureEach {
            reports.create("html") { required.set(true) }
        }
    }
}

// Share declarations only among the modules that already used them, preserving their scopes.
configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-gateway"),
    project(":payguard-identity"),
    project(":payguard-loan"),
    project(":payguard-shared"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.actuator)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-gateway"),
    project(":payguard-identity"),
    project(":payguard-loan"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.webmvc)
            "testImplementation"(rootProject.libs.org.springframework.boot.spring.boot.starter.webmvc.test)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-identity"),
    project(":payguard-loan"),
    project(":payguard-shared"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "testImplementation"(rootProject.libs.org.flywaydb.flyway.database.postgresql)
            "testImplementation"(rootProject.libs.org.testcontainers.testcontainers.postgresql)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-identity"),
    project(":payguard-loan"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "runtimeOnly"(rootProject.libs.org.postgresql.postgresql)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-identity"),
    project(":payguard-loan"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.kafka)
            "testImplementation"(rootProject.libs.org.springframework.boot.spring.boot.starter.flyway)
            "testImplementation"(rootProject.libs.org.testcontainers.testcontainers.junit.jupiter)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-loan"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(project(":payguard-shared"))
        }
    }
}

configure(listOf(
    project(":payguard-gateway"),
    project(":payguard-identity"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.validation)
        }
    }
}

configure(listOf(
    project(":payguard-collateral"),
    project(":payguard-loan"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.data.jpa)
        }
    }
}

configure(listOf(
    project(":payguard-identity"),
    project(":payguard-wallet"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "api"(rootProject.libs.org.springframework.boot.spring.boot.starter.jooq)
            "api"(rootProject.libs.org.springframework.modulith.spring.modulith.starter.core)
            "testImplementation"(rootProject.libs.org.springframework.modulith.spring.modulith.starter.test)
        }
    }
}

configure(listOf(
    project(":payguard-proto"),
    project(":payguard-shared"),
)) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "testImplementation"(rootProject.libs.org.springframework.boot.spring.boot.starter.test)
        }
    }
}

configure(listOf(project(":payguard-identity"), project(":payguard-shared"))) {
    pluginManager.withPlugin("java-library") {
        dependencies {
            "testImplementation"(rootProject.libs.org.postgresql.postgresql)
        }
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}
