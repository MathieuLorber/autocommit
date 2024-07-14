package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import net.mlorber.autocommit.config.Configuration
import net.mlorber.autocommit.watcher.Watcher
import kotlin.concurrent.Volatile


class WatchCommand : CliktCommand("watch") {
    var watchers = listOf<Watcher>()

    @Volatile
    var running: Boolean = true

    override fun run() {
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Stop watchers")
            stopThreads()
            running = false
        })
        watchers = Configuration.repos.map { Watcher(it) }
        while (running) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun stopThreads() {
        watchers.forEach { it.stop() }
        watchers.forEach { it.join() }
    }
}
