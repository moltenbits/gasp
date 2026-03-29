plugins {
    id("gasp.base")
}

dependencies {
    api(project(":gasp-annotations"))
    api(libs.graphql.java)
}
