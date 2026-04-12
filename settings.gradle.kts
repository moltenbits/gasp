pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "gasp"

include("gasp-annotations", "gasp-processor", "gasp-runtime", "gasp-micronaut", "gasp-spring", "gasp-jooq")
include("examples:micronaut-example", "examples:spring-example", "examples:jooq-example")
