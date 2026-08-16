import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    alias(libs.plugins.fabric.loom)
    id("com.gradleup.shadow") version "9.6.1"
}

configurations {
    create("shadowOnly")
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
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
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Meteor
    modImplementation(libs.meteor.client)
    implementation(libs.starscript)

    implementation("com.github.PeaceClient:Peace-IRC:1.0.2")
    "shadowOnly"("com.github.PeaceClient:Peace-IRC:1.0.2") {
        isTransitive = false
    }

    compileOnly(libs.baritone)
}

tasks.shadowJar {
    configurations = listOf(project.configurations["shadowOnly"])
    archiveClassifier.set("unmapped")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile.get())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<Javadoc> {
        (options as StandardJavadocDocletOptions).tags("concept:a:Concept:")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
