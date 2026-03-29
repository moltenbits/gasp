dependencies {
    implementation(project(":gasp-annotations"))

    testImplementation(libs.compile.testing)
    testImplementation(libs.graphql.java)
    testImplementation(libs.jspecify)
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.micronaut.data.model)
    testImplementation(project(":gasp-annotations"))
    testImplementation(project(":gasp-runtime"))
}
