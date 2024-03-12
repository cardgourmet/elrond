import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val artifactVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("maven-publish")
}

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

dependencies {
    implementation("dev.cowzy.cardgourmet:commons-core:0.+")
    implementation("dev.cowzy.cardgourmet:chef-tagger:0.+")
    implementation("dev.cowzy.cardgourmet:chef-farbeagle:0.+")
    implementation("dev.cowzy:kuery-orm:0.+")

    // Coroutine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.6.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.31")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks.kotlinSourcesJar)
        }
    }
    repositories {
        maven {
            name = "cowzyRepositoryReleases"
            url = uri("https://reposilite.cowzy.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}