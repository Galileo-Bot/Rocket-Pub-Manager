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
		name = "Sonatype Snapshots"
		url = uri("https://oss.sonatype.org/content/repositories/snapshots")
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
	implementation(libs.kotlin.stdlib)
	implementation(libs.dotenv)
	
	implementation(libs.groovy)
	implementation(libs.logback)
	implementation(libs.logging)
	
	implementation(libs.serialization)
	implementation(libs.connector)
	implementation(libs.datetime)
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
