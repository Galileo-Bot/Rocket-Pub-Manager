import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.shadow)
    application
}

group = "fr.ayfri"
version = "1.0"

repositories {
    mavenCentral()

    maven {
        name = "Sonatype Releases"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        name = "Sonatype Snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }


    maven {
        name = "Kotlin Discord"
        url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
    }
}

dependencies {
    implementation(libs.kord.extensions)
    implementation(libs.kord.unsafe)
    implementation(libs.kord.base)
    implementation(libs.dotenv)

    implementation(libs.groovy)
    implementation(libs.logback)
    implementation(libs.logging)

    implementation(libs.serialization)
    implementation(libs.connector)
    implementation(libs.datetime)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.freeCompilerArgs = listOf("-Xcontext-receivers")
}

tasks.withType<ShadowJar> {
    manifest.attributes.apply {
        put("Implementation-Title", "Rocket Manager")
        put("Main-Class", "MainKT")
    }
}

application {
    mainClass.set("MainKt")
}
