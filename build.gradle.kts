import dev.kordex.gradle.plugins.i18n.tasks.GenerationTask
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
	implementation(libs.dotenv)
	implementation(libs.kandy.letsPlot)
	implementation(libs.logback)
	implementation(libs.sqlite)
}

application {
	mainClass = "MainKt"
	applicationDefaultJvmArgs = listOf(
		"-XX:+UseContainerSupport",
		"-Xms64m",
		"-Xmx192m",
		"-XX:+UseSerialGC",
		"-XX:+UseCompactObjectHeaders",
		"-XX:MaxMetaspaceSize=128m",
		"-XX:ReservedCodeCacheSize=64m",
		"-XX:MaxDirectMemorySize=32m",
		"-Djava.awt.headless=true",
		"-XX:+ExitOnOutOfMemoryError"
	)
}

kordEx {
	kordExVersion = libs.versions.kord.extensions.get()
	jvmTarget = 25
	/** KordEx 2.5.0-SNAPSHOT targets Kotlin 2.3.10, the project compiles fine on 2.4.10. */
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

val generatedTranslations = layout.buildDirectory.dir("generated/kordex/main/kotlin")

/** The i18n task declares no outputs so Gradle re-runs it every build (~8s): skip it by hand unless a translation source changed. */
tasks.withType<GenerationTask>().configureEach {
	val sources = listOf(
		layout.projectDirectory.dir("src/main/resources/translations").asFile,
		layout.projectDirectory.file(".editorconfig").asFile,
		buildFile
	)
	val generated = generatedTranslations.get().asFile

	mustRunAfter(tasks.named("clean"))
	onlyIf {
		val newest = generated.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
		newest == null || sources.asSequence().flatMap { it.walkTopDown() }.any { it.isFile && it.lastModified() > newest }
	}
}

tasks.matching { it.name == "kspKotlin" || it.name == "compileKotlin" }.configureEach {
	dependsOn(tasks.withType<GenerationTask>())
}

/** The KordEx plugin creates this directory at configuration time, which a reused configuration cache skips. */
tasks.named<Delete>("clean") {
	doLast { generatedTranslations.get().asFile.mkdirs() }
}

/** Downloads every jar the build needs so Docker caches them in their own layer, `dependencies` only resolves metadata. */
tasks.register("warmupDependencies") {
	description = "Resolves all build and runtime artifacts to prime the dependency cache."

	val artifacts = listOf(
		"compileClasspath",
		"runtimeClasspath",
		"kotlinCompilerClasspath",
		"kotlinCompilerPluginClasspathMain",
		"kspKotlinProcessorClasspath",
		"kspPluginClasspath",
		"kordExI18nConfiguration"
	).mapNotNull { configurations.findByName(it)?.incoming?.artifactView { isLenient = true }?.files }

	inputs.files(artifacts)
	doLast { logger.lifecycle("Primed ${artifacts.sumOf { it.count() }} dependency artifacts.") }
}

kotlin {
	jvmToolchain(25)
	compilerOptions.freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
}
