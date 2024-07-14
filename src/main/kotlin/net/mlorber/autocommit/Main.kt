package net.mlorber.autocommit

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.system.exitProcess

data class Repository(val name: String, val path: Path, val ignoreList: List<String>)

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
            // TODO print message if dir is empty
            Repository(
                it.getValue("name"),
                Paths.get(it.getValue("path")),
                it.get("ignore")?.split(",") ?: emptyList()
            )
        }
    }
}

class AutocommitCommand : CliktCommand() {
    override fun run() = Unit
}

class PullCommand : CliktCommand("pull") {
    override fun run() {
        println(Configuration.repos)
    }
}

fun main(args: Array<String>) = AutocommitCommand()
    .subcommands(PullCommand())
    .main(args)
