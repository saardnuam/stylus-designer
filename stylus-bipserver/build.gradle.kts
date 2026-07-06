// BI Publisher web service client (SOAP v2 / REST): catalog browse, download/upload, sample data
plugins {
    `java-library`
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}
