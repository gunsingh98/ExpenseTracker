plugins {
    // Lets Gradle auto-provision the Java 25 toolchain from foojay.io
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "expense-tracker"
