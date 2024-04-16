import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val artifactVersion: String by project

plugins {
    kotlin("jvm") version "1.8.21"
}

allprojects {
    group = "dev.cowzy.cardgourmet"
    version = artifactVersion

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    repositories {
        mavenCentral()

        maven {
            name = "cowzyRepositoryReleases"
            url = uri("https://reposilite.cowzy.dev/releases")
            credentials {
                username = project.findProperty("cowzyRepositoryReleasesUsername")?.toString() ?: System.getenv("MAVEN_USERNAME")
                password = project.findProperty("cowzyRepositoryReleasesPassword")?.toString() ?: System.getenv("MAVEN_PASSWORD")
            }
            authentication { create<BasicAuthentication>("basic") }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}