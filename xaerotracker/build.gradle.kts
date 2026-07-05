plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
}

version = "2.0.0"
group = "info.infinf"

base {
    archivesName = "xaerotracker-fabric"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

val minecraftVersion = "26.1.2"
val loaderVersion = "0.19.3"
val fabricApiVersion = "0.150.0+26.1.2"

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.jar {
    from("LICENSE")
}
