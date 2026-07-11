plugins {
    id("gasp.base")
}

dependencies {
    api(project(":gasp-annotations"))
    api(project(":gasp-runtime"))
    api(libs.jooq)
    implementation(libs.jooq.codegen)

    testImplementation(libs.h2)
    testImplementation(libs.flyway.core)
    testImplementation("org.jooq:jooq-meta:${libs.versions.jooq.get()}")
}
