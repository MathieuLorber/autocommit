package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import mu.KotlinLogging
import net.mlorber.autocommit.config.Configuration
import net.mlorber.autocommit.utils.GitUtils

class UpdateCommand : CliktCommand("update") {

    private val logger = KotlinLogging.logger {}

    override fun run() {
        Configuration.repos.forEach { repositoryConfig -> GitUtils.saveAndUpdate(repositoryConfig) }
    }
}
