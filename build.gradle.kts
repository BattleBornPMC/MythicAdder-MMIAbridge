plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.mythicadder"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.6.1")
    compileOnly("com.github.LoneDev6:api-itemsadder:3.6.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
