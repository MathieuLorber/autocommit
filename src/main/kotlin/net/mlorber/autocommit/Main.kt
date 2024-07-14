package net.mlorber.autocommit

import com.github.ajalt.clikt.core.CliktCommand
import org.yaml.snakeyaml.Yaml
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class Hello : CliktCommand() {
    override fun run() {
        val file = Paths.get(System.getProperty("user.home")).resolve("autocommit-config.yaml")
        if (!file.exists()) {
            println("Missing config file : $file")
        } else {
            val yaml = Yaml().load<Map<String, Any>>(file.inputStream())
            println(yaml.getValue("test"))
        }
    }
}

fun main(args: Array<String>) = Hello().main(args)