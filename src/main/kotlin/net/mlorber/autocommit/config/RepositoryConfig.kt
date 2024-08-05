package net.mlorber.autocommit.config

import java.nio.file.Path
import mu.KotlinLogging

data class RepositoryConfig(
    val name: String,
    val path: Path,
    val branch: String,
    val commitMessagePrefix: String
) {
    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "Init configuration: ${coloredName()} (${path})" }
    }

    fun coloredName() = "${CliColors.blue}$name${CliColors.reset}"
}
