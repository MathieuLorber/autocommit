package net.mlorber.autocommit.config

import org.yaml.snakeyaml.Yaml
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.system.exitProcess

object Configuration {
    val repos by lazy {
        val file = Paths.get(System.getProperty("user.home")).resolve("autocommit-config.yaml")
        if (!file.exists()) {
            println("Missing config file : $file")
            exitProcess(1)
        }
        val yaml = Yaml().load<Map<String, Any>>(file.inputStream())
        val repos = yaml.get("repositories") as List<Map<String, String>>
        repos.map {
            Repository(
                it.getValue("name"),
                Paths.get(it.getValue("path")),
                it.get("ignore")?.split(",") ?: emptyList()
            )
        }
    }
}