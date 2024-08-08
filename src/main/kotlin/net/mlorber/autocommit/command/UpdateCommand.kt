package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import kotlin.io.path.exists
import mu.KotlinLogging
import net.mlorber.autocommit.config.Configuration
import net.mlorber.autocommit.thread.UpdateThread
import net.mlorber.autocommit.utils.GitUtils

class UpdateCommand : CliktCommand("update") {

    override fun run() {
        Configuration.repos.forEach { UpdateThread(it) }
    }
}
