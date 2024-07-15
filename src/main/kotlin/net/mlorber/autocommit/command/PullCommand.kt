package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import net.mlorber.autocommit.config.Configuration

class PullCommand : CliktCommand("pull") {
    override fun run() {
        println(Configuration.repos)
    }
}
