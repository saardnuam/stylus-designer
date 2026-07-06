// Model → XSLT/XSL-FO writer + XSL → model reader (round-trip, N7)
plugins {
    `java-library`
}

dependencies {
    api(project(":stylus-model"))

    // Test-only: prove generated templates really render (F-11.5)
    testImplementation(project(":stylus-engine-fop"))
    testImplementation(libs.pdfbox)
    testRuntimeOnly(libs.slf4j.simple)
}

// Golden-file harness (F-11.4): expected files live in the source tree so
// -Dgolden.update=true can rewrite them; actuals land in build/ for diffing.
tasks.test {
    systemProperty("golden.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    systemProperty("golden.actual.dir", layout.buildDirectory.dir("golden-actual").get().asFile.absolutePath)
    systemProperty("golden.update", System.getProperty("golden.update", "false"))
}
