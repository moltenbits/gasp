plugins {
    id("gasp.base")
    id("io.micronaut.library")
}

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    api(project(":gasp-runtime"))
    api(libs.micronaut.graphql)
}
