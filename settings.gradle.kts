pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NMEARouter"
include(":app")

// marine-kit is a git submodule pinned at a release tag, built from source.
// The included build needs the SDK path; local.properties is not in git, so copy ours when it is missing.
val kitProps = file("marine-kit/local.properties")
if (!kitProps.exists()) {
    val sdk = java.util.Properties().apply { file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }
        .getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    if (sdk != null) kitProps.writeText("sdk.dir=" + sdk.replace("\\", "\\\\") + "\n")
}
includeBuild("marine-kit") {
    dependencySubstitution {
        substitute(module("com.github.titio72.marine-kit:ui")).using(project(":ui"))
        substitute(module("com.github.titio72.marine-kit:link")).using(project(":link"))
    }
}
 