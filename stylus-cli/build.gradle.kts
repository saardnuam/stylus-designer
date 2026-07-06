// Headless CLI: render templates from the command line (M1); also drives CI rendering tests
plugins {
    application
}

dependencies {
    implementation(project(":stylus-engine-api"))
    implementation(project(":stylus-engine-fop"))
    implementation(project(":stylus-engine-bip"))
    implementation(project(":stylus-config"))
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("dev.stylus.cli.StylusCli")
}

// `./stylus cli render --template samples/…` should resolve paths from the repo root,
// not from this module directory.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
