import com.github.gradle.node.npm.task.NpmTask

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.expensetracker"
version = "0.1.0"

java {
    toolchain {
        // Latest Java; virtual threads require 21+, we target 25.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Audit log persistence: a dedicated SQLite DB (separate from the workbook),
    // accessed with Spring's JdbcTemplate (no JPA/ORM, matching the project style).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.xerial:sqlite-jdbc:3.50.1.0")

    // Excel (xlsx) read/write — preserves formulas & charts on round-trip.
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // Google Drive API + service-account auth.
    implementation("com.google.apis:google-api-services-drive:v3-rev20260916-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.54.0")
    implementation("com.google.api-client:google-api-client:2.9.1")
    implementation("com.google.http-client:google-http-client-jackson2:2.2.0")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = "9.2.0"
    distributionType = Wrapper.DistributionType.BIN
    // Proxy resets the JVM's HEAD pre-check; the real GET download works fine.
    validateDistributionUrl = false
}

/* ---------------------------------------------------------------------------
 * Frontend (React + Vite) build wired into the Spring Boot jar.
 * `vite build` output (frontend/dist) is copied into resources/static so the
 * single Spring Boot service serves both the API and the SPA.
 * ------------------------------------------------------------------------- */
node {
    version = "24.21.0"
    download = false // use the Node already installed on this machine
    nodeProjectDir = file("${project.projectDir}/frontend")
}

val npmInstall = tasks.named("npmInstall")

val buildFrontend = tasks.register<NpmTask>("buildFrontend") {
    dependsOn(npmInstall)
    args = listOf("run", "build")
    inputs.dir("frontend/src")
    inputs.file("frontend/package.json")
    inputs.file("frontend/vite.config.ts")
    inputs.file("frontend/index.html")
    outputs.dir("frontend/dist")
}

val copyFrontend = tasks.register<Copy>("copyFrontend") {
    dependsOn(buildFrontend)
    from("frontend/dist")
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("processResources") {
    // Only bundle the SPA when it has been built; keeps `test`/dev fast.
    if (project.hasProperty("withFrontend")) {
        dependsOn(copyFrontend)
    }
}
