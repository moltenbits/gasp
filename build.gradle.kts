subprojects {
    apply(plugin = "java-library")
    apply(plugin = "groovy")

    group = "com.moltenbits.gasp"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "testImplementation"(platform(rootProject.libs.groovy.bom))
        "testImplementation"(platform(rootProject.libs.spock.bom))
        "testImplementation"(rootProject.libs.spock.core)
        "testImplementation"(rootProject.libs.groovy.all)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<AbstractTestTask>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
