plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "us.ajg0702"
version = "3.0.0"

val minecraftVersion = "26.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.5") { isTransitive = false }
    compileOnly(files("libs/InfiniteJump.jar"))

    implementation("com.zaxxer:HikariCP:7.1.0") { exclude(group = "org.slf4j") }
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-parameters"))
    }

    processResources {
        val props = mapOf("version" to project.version, "apiVersion" to minecraftVersion)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            filter<org.apache.tools.ant.filters.ReplaceTokens>(
                "tokens" to mapOf("VERSION" to props["version"].toString(), "API_VERSION" to props["apiVersion"].toString())
            )
        }
    }

    shadowJar {
        archiveClassifier = ""
        relocate("com.zaxxer.hikari", "us.ajg0702.parkour.libs.hikari")
        relocate("org.bstats", "us.ajg0702.parkour.libs.bstats")
        minimize {
            exclude(dependency("com.zaxxer:HikariCP:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraftVersion)
    }
}

runPaper.folia.registerTask()

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks["jar"])
        }
    }

    repositories {
        val mavenUrl = "https://repo.ajg0702.us/releases"

        if (!System.getenv("REPO_TOKEN").isNullOrEmpty()) {
            maven {
                url = uri(mavenUrl)
                name = "ajRepo"

                credentials {
                    username = "plugins"
                    password = System.getenv("REPO_TOKEN")
                }
            }
        }
    }
}
