
rootProject.name = "Rocket-Manager"

enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			from(files("libs.versions.toml"))
		}
	}
}
