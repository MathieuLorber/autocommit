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
            // ❌ à retirer si vous l’aviez :
            // buildArgs.add("--initialize-at-build-time=com.sun.jna,com.sun.jna.*")

            // ✅ corrige l’erreur: JNA s'initialise au runtime (pas de thread dans l'image)
            buildArgs.add("--initialize-at-run-time=com.sun.jna,com.sun.jna.*")
            // moins : com.sun.jna.internal.Cleaner,com.sun.jna.Native,com.sun.jna.NativeLibrary
            // Recos JNA
            runtimeArgs.add("-Djna.nosys=true")

            // (optionnel) debug si besoin
            // buildArgs.add("--verbose")
            // buildArgs.add("--trace-object-instantiation=com.sun.jna.internal.Cleaner$CleanerThread")
        }
    }
}
