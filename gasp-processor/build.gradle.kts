plugins {
    id("gasp.base")
}

dependencies {
    implementation(project(":gasp-annotations"))

    testImplementation(libs.compile.testing)
    testImplementation(libs.graphql.java)
    testImplementation(libs.jspecify)
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.micronaut.data.model)
    testImplementation("jakarta.inject:jakarta.inject-api:2.0.1")
    testImplementation(project(":gasp-annotations"))
    testImplementation(project(":gasp-runtime"))
}
