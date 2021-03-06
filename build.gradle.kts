import com.google.javascript.jscomp.PropertyRenamingPolicy

// Pull the plugin from a Maven Repo
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.eriwen:gradle-js-plugin:1.4.0")
    }
}

plugins {
    java
}

version = project.property("rtmMcWrapperVersion").toString()

repositories {
    jcenter()
    maven(url="https://libraries.minecraft.net/")
}

/**
 * a source set for generating .d.ts of jdk classes
 */
val dtsJdk = sourceSets.create("dtsJdk") {
    java {
        setSrcDirs(listOf("src/dtsJdk"))
    }
    resources.srcDirs.clear()
}

val apiComm = sourceSets.create("apiComm") {
    java {
        setSrcDirs(listOf("src/apiCommManually"))
    }
    resources.srcDirs.clear()
}
val apiCommImplementation = configurations.getByName("apiCommImplementation")

val names = listOf("Comm", "1122", "1710")

for (name in names) {
    val main = sourceSets.create("main$name") {
        java {
            setSrcDirs(mutableListOf("src/main$name"))
        }
        resources.srcDirs.clear()
    }

    val mainImplementation = configurations.getByName("main${name}Implementation")

    dependencies {
        mainImplementation(files("build/generated/api$name.jar"))
        mainImplementation(apiComm.runtimeClasspath)
        if (name != "Comm") {
            mainImplementation(sourceSets.getByName("mainComm").runtimeClasspath)
        }
    }

    tasks.getByName(main.compileJavaTaskName)
            .dependsOn(":dts-generator:generate${name}Java")
}

val main1122 by sourceSets
val main1710 by sourceSets
val mainComm by sourceSets
val test: SourceSet by sourceSets.getting {
    java {
        setSrcDirs(mutableListOf("src/test"))
    }
    resources.srcDirs.clear()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    apiCommImplementation("org.apache.logging.log4j:log4j-core:2.8.1")
    apiCommImplementation("com.google.guava:guava:21.0")
    apiCommImplementation("com.google.code.gson:gson:2.8.0")
    apiCommImplementation("net.minecraft:launchwrapper:1.12")

    testImplementation(mainComm.runtimeClasspath)
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
}

mainComm.apply {
    resources {
        srcDir("src/mainCommResources")
    }
}

tasks.jar {
    archiveExtension.set("zip")

    for (main in listOf(main1122, main1710, mainComm)) {
        from(main.output.classesDirs)
        from(main.output.resourcesDir!!) {
            expand("version_name" to project.version)
        }
    }
    dependsOn(":dts-generator:generateApiDts", ":dts-generator:generateIncludedV2Dts")
    from("build/generated/api.d.ts")
    from("build/generated/api.included.v2.d.ts")
    manifest {
        attributes("FMLAT" to "anatawa12_rtm_mc_wrapper_at.cfg")
    }
}

val minifyjs by tasks.creating {
    val MINIFIER = com.eriwen.gradle.js.JsMinifier()
    val compilerOptions = com.google.javascript.jscomp.CompilerOptions().apply {
        propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED
        setLineLengthThreshold(Int.MAX_VALUE)
    }

    doLast {
        val resourceSrc = mainComm.resources.srcDirs.last()
        val resourceOut = mainComm.output.resourcesDir!!
        resourceSrc!!.walk().forEach {
            if (!it.isFile) return@forEach
            if (!it.name.endsWith("-big.js")) return@forEach
            val dest = resourceOut
                    .resolve(it.parentFile.relativeTo(resourceSrc))
                    .resolve(it.name.substringBeforeLast('-') + ".js")

            MINIFIER.minifyJsFile(setOf(it), emptySet(), dest, compilerOptions,
                    "QUIET", "SIMPLE_OPTIMIZATIONS")
        }
    }
}

tasks.getByName("processMainCommResources").dependsOn(minifyjs)

