import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kordexVersion: String by project
val kordVersion: String by project
val dotEnvVersion: String by project
val exposedVersion: String by project

plugins {
	kotlin("jvm") version "1.4.32"
	kotlin("plugin.serialization") version "1.4.32"
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
	implementation("com.kotlindiscord.kord.extensions:kord-extensions:$kordexVersion")
	implementation("io.github.cdimascio:dotenv-kotlin:$dotEnvVersion")
	
	implementation("ch.qos.logback:logback-classic:1.2.3")
	implementation("io.github.microutils:kotlin-logging:2.0.6")
	implementation("org.codehaus.groovy:groovy:3.0.7")
	
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
	implementation("mysql:mysql-connector-java:8.0.23")
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = "15"
	
	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

application {
	mainClassName = "MainKt"
}
