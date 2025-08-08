plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

group = "dev.treehouse"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}
