// Bundled engine: Saxon (XSLT) + Apache FOP (FO → PDF/PS/PCL/AFP/PNG/TIFF/TXT), fop.xconf handling
plugins {
    `java-library`
}

dependencies {
    api(project(":stylus-engine-api"))
    implementation(libs.saxon.he)
    // The aggregate fop artifact adds Batik so instream SVG renders (F-7.2).
    implementation(libs.fop)
    implementation(libs.slf4j.api)

    testImplementation(libs.pdfbox)
    testRuntimeOnly(libs.slf4j.simple)
}
