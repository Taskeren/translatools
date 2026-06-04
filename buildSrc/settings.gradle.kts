dependencyResolutionManagement {
    versionCatalogs {
        // use the version catalog of the root project
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
