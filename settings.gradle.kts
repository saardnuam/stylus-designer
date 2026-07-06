pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK toolchain in CI / fresh machines
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "stylus"

include(
    ":stylus-model",
    ":stylus-codegen",
    ":stylus-engine-api",
    ":stylus-engine-fop",
    ":stylus-engine-bip",
    ":stylus-bipserver",
    ":stylus-config",
    ":stylus-xliff",
    ":stylus-cli",
    ":stylus-app",
)
