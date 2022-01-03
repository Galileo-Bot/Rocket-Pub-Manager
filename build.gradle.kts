import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.6.10"
	kotlin("plugin.serialization") version "1.6.10"
	id("com.github.johnrengelman.shadow") version "7.1.2"
	application
}

group = "fr.ayfri"
version = "1.0"

repositories {
	mavenCentral()
	
	maven {
		name = "Kotlin Discord"
		url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
	}
}

dependencies {
	implementation(libs.kord.extensions)
	implementation(libs.kotlin.stdlib)
	implementation(libs.dotEnv)
	
	implementation(libs.groovy)
	implementation(libs.logback)
	implementation(libs.logging)
	
	implementation(libs.serialization)
	implementation(libs.connector)
}

tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = "17"
	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
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
