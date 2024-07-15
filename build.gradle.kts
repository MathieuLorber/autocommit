group = "net.mlorber.autocommit"

version = "0.1"

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.graalvm.buildtools.native") version "0.10.2"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.yaml:snakeyaml:2.2")
}

repositories { mavenCentral() }

graalvmNative {
    binaries {
        named("main") {
            imageName.set("autocommit")
            mainClass.set("net.mlorber.autocommit.MainKt")
        }
    }
}
