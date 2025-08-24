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
    // pv remove ?
    compileOnly("org.graalvm.nativeimage:svm:21.0.0.2")
}

repositories { mavenCentral() }

graalvmNative {
    binaries {
        named("main") {
            imageName.set("autocommit")
            mainClass.set("net.mlorber.autocommit.MainKt")

            // IMPORTANT : JNA doit s'initialiser au runtime (sinon thread Cleaner figé)
            buildArgs.add("--initialize-at-run-time=com.sun.jna,com.sun.jna.*")

            // Charger *explicitement* le proxy-config depuis les ressources
            buildArgs.add("-H:DynamicProxyConfigurationResources=META-INF/native-image/proxy-config.json")

            // JNA : ne pas chercher une lib système externe
            runtimeArgs.add("-Djna.nosys=true")

            // (optionnel) debug:
            // buildArgs.add("--verbose")
            // buildArgs.add("-H:+ReportExceptionStackTraces")
            // (optionnel) si jamais jnidispatch pose souci chez vous :
            // buildArgs.add("-H:IncludeResources=.*jnidispatch.*")
        }
    }
}
