import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	alias(libs.plugins.kotlin)
	alias(libs.plugins.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.kordex)
	application
	distribution
}

group = "fr.ayfri"
version = "1.0"

dependencies {
	implementation(libs.connector)
	implementation(libs.datetime)
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
	jvmTarget = 21

	bot {
		dataCollection(DataCollection.None)
		mainClass = "MainKt"
		voice = false
	}

	i18n {
		classPackage = "fr.ayfri.rocketmanager.i18n"
		translationBundle = "rocketmanager.strings"
	}
}

kotlin {
	jvmToolchain(21)

	compilerOptions {
		freeCompilerArgs = listOf("-Xcontext-receivers", "-opt-in=kotlin.time.ExperimentalTime")
	}
}
