// JavaFX application: shell, canvas, panels, preview, test bench, i18n
plugins {
    application
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.web", "javafx.swing")
}

dependencies {
    implementation(project(":stylus-model"))
    implementation(project(":stylus-codegen"))
    implementation(project(":stylus-engine-api"))
    implementation(project(":stylus-engine-fop"))
    implementation(project(":stylus-engine-bip"))
    implementation(project(":stylus-bipserver"))
    implementation(project(":stylus-config"))
    implementation(project(":stylus-xliff"))
    implementation(libs.richtextfx)
    implementation(libs.pdfbox)
    implementation(libs.saxon.he) // expression editor: live XPath validate + preview (F-1.32)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("dev.stylus.app.Main")
}

// Forward the dev/test working-dir hook into the launched app:
//   ./gradlew :stylus-app:run -Dstylus.workingDir=/path/to/samples
tasks.named<JavaExec>("run") {
    System.getProperty("stylus.workingDir")?.let { systemProperty("stylus.workingDir", it) }
}

// ---------- native installer (M7 packaging) ----------
// `./gradlew :stylus-app:jpackage` → build/dist/Stylus-<version>.dmg (macOS; app-image
// elsewhere). Bundles a trimmed JRE; JavaFX rides along as classpath jars with natives.
// Never includes Oracle jars (hard rule 1): the BIP adapter loads them from the user's
// installation at runtime.

val appVersion = "1.0.0" // macOS CFBundleVersion: first number must be >= 1
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")

val jpackageInput = tasks.register<Copy>("jpackageInput") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(jpackageInputDir)
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds a native installer with a bundled runtime"
    dependsOn(jpackageInput)

    val dist = layout.buildDirectory.dir("dist")
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    doFirst {
        delete(dist) // jpackage refuses to overwrite an existing image
    }
    val args = mutableListOf(
        "${System.getProperty("java.home")}/bin/jpackage",
        "--type", if (isMac) "dmg" else "app-image",
        "--name", "Stylus",
        "--app-version", appVersion,
        "--input", jpackageInputDir.get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "dev.stylus.app.Main",
        "--dest", dist.get().asFile.absolutePath,
        "--java-options", "-Xmx1g",
    )
    if (isMac) {
        args += listOf("--icon", layout.projectDirectory.file("packaging/Stylus.icns").asFile.absolutePath)
    }
    commandLine(args)
}
