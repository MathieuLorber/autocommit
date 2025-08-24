group = "net.mlorber.autocommit"

version = "0.1"

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.graalvm.buildtools.native") version "0.10.2"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("net.java.dev.jna:jna:5.14.0")
}

repositories { mavenCentral() }

graalvmNative {
    binaries {
        named("main") {
            imageName.set("autocommit")
            mainClass.set("net.mlorber.autocommit.MainKt")

            // ❌ NE PAS mettre -H:+AddFirstThreadInitializer (option absente)
            // JNA : souvent inutile d'inclure manuellement jnidispatch, mais possible :
            buildArgs.add("-H:IncludeResources=.*jnidispatch.*")
            // (optionnel) verbosité / rapport
            // buildArgs.add("--verbose")

            // Runtime
            runtimeArgs.add("-Djna.nosys=true")
            // Si app headless:
            // runtimeArgs.add("-Djava.awt.headless=true")
        }
    }
}
