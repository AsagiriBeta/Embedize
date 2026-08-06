plugins {
    java
}

group = "com.embedize"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Official Multiverse Maven — https://mvplugins.org/core/developers/developer-api-starter/
    maven("https://repo.onarandombox.com/content/groups/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    // Multiverse-Core 5 API (softdepend at runtime).
    // Docs: https://mvplugins.org/core/developers/developer-api-starter/
    //       https://mvplugins.org/core/developers/api-usage/
    // Uses relocated vavr: org.mvplugins.multiverse.external.vavr
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.7.3")

    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("Embedize")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}
