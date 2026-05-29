plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
}

stonecutter active "1.21.4" /* [SC] DO NOT EDIT */

tasks.register("buildAllAndCollect") {
    group = "build"
    description = "Builds all Stonecutter versions and collects jars into build/libs/<mod version>/"
    stonecutter.versions.forEach { ver ->
        dependsOn(":${ver.version}:buildAndCollect")
    }
}
