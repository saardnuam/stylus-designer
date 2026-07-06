// Oracle BI Publisher adapter: loads user-local BIP jars via isolated classloader (reflection).
// HARD RULE: no Oracle jar is ever a compile/runtime dependency or bundled artifact.
plugins {
    `java-library`
}

dependencies {
    api(project(":stylus-engine-api"))
    implementation(project(":stylus-config"))
    implementation(libs.slf4j.api)

    testImplementation(libs.pdfbox) // text assertions on BIP-rendered PDFs
    testRuntimeOnly(libs.slf4j.simple)
}
