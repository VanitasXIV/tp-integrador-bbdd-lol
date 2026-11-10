plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
}

group = "ar.edu.unsam"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.influxdb:influxdb-client-java:7.2.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        javaParameters = true
    }
}

// La JVM corre en UTC (ver LolBackendApplication.kt): tests y bootRun incluidos.
tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "UTC")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("user.timezone", "UTC")
}

// `./gradlew test` corre solo los tests unitarios (no necesitan bases).
// `./gradlew testIntegracion` corre los que usan PostgreSQL e InfluxDB:
// requieren `docker compose up -d` en la raíz del repo.
tasks.test {
    useJUnitPlatform {
        excludeTags("integracion")
    }
}

tasks.register<Test>("testIntegracion") {
    description = "Tests contra PostgreSQL e InfluxDB levantados con Docker Compose"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integracion")
    }
}
