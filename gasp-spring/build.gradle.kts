plugins {
    id("gasp.base")
}

dependencies {
    api(project(":gasp-runtime"))
    api(libs.spring.graphql)
    implementation("org.springframework.boot:spring-boot-autoconfigure:${libs.versions.spring.boot.get()}")
}
