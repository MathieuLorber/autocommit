package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import java.time.Duration
import kotlin.concurrent.Volatile
import mu.KotlinLogging
import net.mlorber.autocommit.config.Configuration
import net.mlorber.autocommit.puller.PeriodicPuller
import net.mlorber.autocommit.watcher.Watcher

class WatchCommand : CliktCommand("watch") {

    private val logger = KotlinLogging.logger {}

    private lateinit var watchers: List<Watcher>
    private lateinit var pullers: List<PeriodicPuller>

    @Volatile var running: Boolean = true

    override fun run() {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    stopThreads()
                    running = false
                })
        
        watchers = Configuration.repos.map { Watcher(it) }
        
        pullers = Configuration.repos.map { repo ->
            PeriodicPuller(
                repo,
                Configuration.pullInterval,
                Configuration.checkRemoteFirst
            ).also { it.start() }
        }
        
        while (running) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun stopThreads() {
        logger.info { "Stop watchers and pullers" }
        pullers.forEach { it.stop() }
        watchers.forEach { it.stop() }
        watchers.forEach { it.join() }
    }
}
