package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import kotlin.concurrent.Volatile
import mu.KotlinLogging
import net.mlorber.autocommit.config.Configuration
import net.mlorber.autocommit.thread.WatchThread

class WatchCommand : CliktCommand("watch") {

    private val logger = KotlinLogging.logger {}

    private lateinit var watchers: List<WatchThread>

    @Volatile var running: Boolean = true

    override fun run() {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    stopThreads()
                    running = false
                })
        watchers = Configuration.repos.map { WatchThread(it) }
        while (running) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun stopThreads() {
        logger.info { "Stop watchers" }
        watchers.forEach { it.stop() }
        watchers.forEach { it.join() }
    }
}
