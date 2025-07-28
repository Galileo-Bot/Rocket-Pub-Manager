import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	alias(libs.plugins.kotlin)
	alias(libs.plugins.serialization)
	alias(libs.plugins.shadow)
	alias(libs.plugins.ksp)
	alias(libs.plugins.kordex)
}

group = "fr.ayfri"
version = "1.0"

dependencies {
	implementation(libs.dotenv)

	implementation(libs.logback)

	implementation(libs.connector)
	implementation(libs.datetime)
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

tasks.withType<ShadowJar> {
	manifest {
		attributes(
			"Implementation-Title" to "Rocket Manager",
			"Main-Class" to "fr.ayfri.rocketmanager.MainKt",
			"Implementation-Version" to project.version
		)
	}

	mergeServiceFiles()
	archiveClassifier.set("")
}
