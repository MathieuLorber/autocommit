package net.mlorber.autocommit

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import net.mlorber.autocommit.command.UpdateCommand
import net.mlorber.autocommit.command.WatchCommand

class AutocommitCommand : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) =
    AutocommitCommand().subcommands(UpdateCommand(), WatchCommand()).main(args)
