val artifactVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project
val commonsVersion: String by project
val chefVersion: String by project
val kueryVersion: String by project

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("maven-publish")
}

dependencies {
    implementation(project(":elrond-core"))
    implementation("dev.cowzy.cardgourmet:commons-core:$commonsVersion")
    implementation("dev.cowzy.cardgourmet:commons-user:$commonsVersion")
    implementation("dev.cowzy.cardgourmet:commons-auth:$commonsVersion")
    implementation("dev.cowzy.cardgourmet:chef-commons:$chefVersion")
    implementation("dev.cowzy:kuery-orm:$kueryVersion")

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