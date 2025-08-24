package net.mlorber.autocommit.config

import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.system.exitProcess
import mu.KotlinLogging
import org.yaml.snakeyaml.Yaml

object Configuration {

    private val logger = KotlinLogging.logger {}
    
    private val yaml by lazy {
        val file = Paths.get(System.getProperty("user.home")).resolve("autocommit-config.yaml")
        if (!file.exists()) {
            logger.error { "Missing config file : $file" }
            exitProcess(1)
        }
        Yaml().load<Map<String, Any>>(file.inputStream())
    }

    val repos by lazy {
        val commonPrefix  = yaml.get("commitMessagePrefix") as String?
        // TODO a cleaner check of conf ?
        @Suppress("UNCHECKED_CAST")
        val repos = yaml.get("repositories") as List<Map<String, String>>
        repos.map {
            RepositoryConfig(
                it.getValue("name"),
                Paths.get(it.getValue("path")),
                it.getValue("branch"),
                it.get("commitMessagePrefix") ?: commonPrefix ?: "")
        }
    }
    
    val pullInterval: Duration by lazy {
        val minutes = (yaml.get("pullIntervalMinutes") as? Number)?.toLong() ?: 5L
        Duration.ofMinutes(minutes)
    }
    
    val checkRemoteFirst: Boolean by lazy {
        yaml.get("checkRemoteFirst") as? Boolean ?: false
    }
}
