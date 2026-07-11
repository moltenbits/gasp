buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-core:12.3.0")
        classpath("com.h2database:h2:2.4.240")
    }
}

plugins {
    id("java")
    id("groovy")
    id("org.jooq.jooq-codegen-gradle") version "3.21.1"
}

version = "0.1"
group = "com.example"

val dbUrl = "jdbc:h2:${layout.buildDirectory.get()}/h2/gasp;AUTO_SERVER=TRUE"
val dbUser = "sa"
val dbPassword = ""

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    annotationProcessor(project(":gasp-processor"))

    implementation(project(":gasp-annotations"))
    implementation(project(":gasp-runtime"))
    implementation(project(":gasp-jooq"))
    implementation(rootProject.libs.jooq)
    implementation(rootProject.libs.h2)
    implementation(rootProject.libs.flyway.core)
    implementation(rootProject.libs.graphql.java)
    implementation(rootProject.libs.jspecify)
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")

    jooqCodegen(rootProject.libs.h2)
    jooqCodegen(project(":gasp-jooq"))

    testImplementation(platform(rootProject.libs.groovy.bom))
    testImplementation(platform(rootProject.libs.spock.bom))
    testImplementation(rootProject.libs.spock.core)
    testImplementation(rootProject.libs.groovy.all)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Run Flyway migrations before jOOQ codegen
val flywayMigrate by tasks.registering {
    doLast {
        val flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(dbUrl, dbUser, dbPassword)
            .locations("filesystem:${projectDir}/src/main/resources/db/migration")
            .load()
        flyway.migrate()
    }
}

jooq {
    configuration {
        jdbc {
            driver = "org.h2.Driver"
            url = dbUrl
            user = dbUser
            password = dbPassword
        }
        generator {
            name = "com.moltenbits.gasp.jooq.GaspJooqGenerator"
            database {
                name = "org.jooq.meta.h2.H2Database"
                inputSchema = "PUBLIC"
                excludes = "flyway_schema_history"
            }
            generate {
                isDeprecated = false
                isRecords = true
                isPojos = true
                isFluentSetters = true
            }
            target {
                packageName = "com.example.jooq"
                directory = "${layout.buildDirectory.get()}/generated/sources/jooq"
            }
        }
    }
}

tasks.named("jooqCodegen") {
    dependsOn(flywayMigrate)
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get()}/generated/sources/jooq")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("jooqCodegen")
}

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
