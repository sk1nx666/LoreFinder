plugins {
    id("fabric-loom")
}

val minecraftVersion = stonecutter.current.version
val modVersion = project.property("mod.version") as String
val mavenGroup = project.property("mod.group") as String

val yarnMappings = project.property("deps.yarn_mappings") as String
val loaderVersion = project.property("deps.fabric_loader") as String
val meteorVersion = project.property("deps.meteor_version") as String

base {
    archivesName.set(project.property("mod.id") as String)
    version = "${modVersion}+mc${minecraftVersion}"
    group = mavenGroup
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modCompileOnly("meteordevelopment:meteor-client:$meteorVersion")
}

stonecutter {
    replacements {
        string(current.parsed <= "1.21.1") {
            replace("getTopYInclusive()", "getTopY() - 1")
        }
        string(current.parsed >= "1.21.11") {
            replace("marker.getEntityId()", "marker.entityId()")
        }
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.property("mod.version"),
            "mc_targets" to project.property("mod.mc_targets")
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    val buildAndCollect = register<Copy>("buildAndCollect") {
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
        dependsOn("build")
    }

    if (stonecutter.current.isActive) {
        register("buildActive") {
            group = "project"
            dependsOn(buildAndCollect)
        }

        register("runActive") {
            group = "project"
            dependsOn(named("runClient"))
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
