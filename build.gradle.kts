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

// The i18n plugin declares no outputs and one non-serializable `@Input`, so Gradle can neither
// fingerprint nor skip its task: it re-runs on every build and takes ~8s of a ~10s incremental build.
// Declaring the outputs here makes Gradle fingerprint the broken input and fail, so compare the
// translation bundles against the generated sources by hand instead.
tasks.withType<dev.kordex.gradle.plugins.i18n.tasks.GenerationTask>().configureEach {
	val sources = listOf(
		layout.projectDirectory.dir("src/main/resources/translations").asFile,
		layout.projectDirectory.file(".editorconfig").asFile,
		buildFile
	)
	val generated = layout.buildDirectory.dir("generated/kordex/main/kotlin").get().asFile

	// Without declared outputs nothing orders this task after `clean`, which would wipe what it wrote.
	mustRunAfter(tasks.named("clean"))

	onlyIf {
		val newestGenerated = generated.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
		newestGenerated == null || sources.asSequence()
			.flatMap { it.walkTopDown() }
			.any { it.isFile && it.lastModified() > newestGenerated }
	}
}

// Both compilers read the generated translations, but the plugin never wires the dependency up. It
// happened to work only because the task above used to run unconditionally on every build.
val generateTranslations = tasks.withType<dev.kordex.gradle.plugins.i18n.tasks.GenerationTask>()

tasks.matching { it.name == "kspKotlin" || it.name == "compileKotlin" }.configureEach {
	dependsOn(generateTranslations)
}

// The KordEx plugin creates its generated source directory while configuring the project. When the
// configuration cache is reused that step never runs, so a `clean` in the same invocation leaves the
// directory missing and every task reading the source set fails.
tasks.named<Delete>("clean") {
	val generatedSources = layout.buildDirectory.dir("generated/kordex/main/kotlin")
	doLast {
		generatedSources.get().asFile.mkdirs()
	}
}

// Downloads every artifact the build needs so Docker can cache them in a layer of their own,
// before the sources are copied in. `dependencies` only resolves metadata, not the jars.
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
	).mapNotNull { name ->
		configurations.findByName(name)?.incoming?.artifactView { isLenient = true }?.files
	}

	inputs.files(artifacts)
	doLast {
		logger.lifecycle("Primed ${artifacts.sumOf { it.count() }} dependency artifacts.")
	}
}

kotlin {
	jvmToolchain(25)

	compilerOptions {
		freeCompilerArgs = listOf("-opt-in=kotlin.time.ExperimentalTime")
	}
}
