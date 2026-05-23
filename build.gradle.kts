plugins {
    java
    // Removed the paperweight plugin because depending on it makes 
    // cross-version compatibility much harder.
}

group = "dev.treehouse"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Replaced paperDevBundle with paper-api. 
    // compileOnly ensures it's used for building but not packaged in your jar.
    // You can update "1.21.1" to a newer version here if you need newer API features,
    // but building against 1.21.1 will still run perfectly on newer versions!
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT") 
    
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
