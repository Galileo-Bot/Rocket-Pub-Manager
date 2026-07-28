import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	alias(libs.plugins.kotlin)
	alias(libs.plugins.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.kordex)
	alias(libs.plugins.kordex.i18n)
	application
}

group = "fr.ayfri"
version = "1.0"

dependencies {
	implementation(libs.connector)
	implementation(libs.dotenv)
	implementation(libs.logback)
}

application {
	mainClass = "MainKt"
	applicationDefaultJvmArgs = listOf(
		"-XX:+UseContainerSupport",
		"-XX:MaxRAMPercentage=80.0",
		"-XX:+ExitOnOutOfMemoryError"
	)
}

kordEx {
	kordExVersion = libs.versions.kord.extensions.get()
	jvmTarget = 25

	// KordEx 2.5.0-SNAPSHOT is built against Kotlin 2.3.10, the project compiles fine on 2.4.10.
	ignoreIncompatibleKotlinVersion = true

	bot {
		dataCollection(DataCollection.None)
		mainClass = "MainKt"
		voice = false
	}
}

i18n {
	bundle("rocketmanager.strings", "fr.ayfri.rocketmanager.i18n")
}

kotlin {
	jvmToolchain(25)

	compilerOptions {
		freeCompilerArgs = listOf("-opt-in=kotlin.time.ExperimentalTime")
	}
}
