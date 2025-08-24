package net.mlorber.autocommit.puller

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import mu.KotlinLogging
import net.mlorber.autocommit.config.RepositoryConfig
import net.mlorber.autocommit.utils.GitUtils

class PeriodicPuller(
    private val repositoryConfig: RepositoryConfig,
    private val pullInterval: Duration = Duration.ofMinutes(5),
    private val checkRemoteFirst: Boolean = false
) {
    private val logger = KotlinLogging.logger {}
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    @Volatile private var running = true
    
    fun start() {
        executor.scheduleAtFixedRate(
            {
                if (running) {
                    performPull()
                }
            },
            pullInterval.toMillis(),
            pullInterval.toMillis(),
            TimeUnit.MILLISECONDS
        )
        logger.info { "Started periodic puller for ${repositoryConfig.name} with interval ${pullInterval.toMinutes()} minutes" }
    }
    
    private fun performPull() {
        try {
            val currentBranch = GitUtils.currentBranch(repositoryConfig)
            if (currentBranch != repositoryConfig.branch) {
                logger.debug { "${repositoryConfig.name}: Skipping pull - not on configured branch (current: $currentBranch, expected: ${repositoryConfig.branch})" }
                return
            }
            
            if (checkRemoteFirst && !hasRemoteChanges()) {
                logger.debug { "${repositoryConfig.name}: No remote changes detected, skipping pull" }
                return
            }
            
            logger.info { "${repositoryConfig.name}: Performing periodic pull" }
            GitUtils.pull(repositoryConfig)
        } catch (e: Exception) {
            logger.error(e) { "${repositoryConfig.name}: Error during periodic pull" }
        }
    }
    
    private fun hasRemoteChanges(): Boolean {
        return try {
            val remoteChecker = GitHubRemoteChecker(repositoryConfig)
            remoteChecker.hasRemoteChanges()
        } catch (e: Exception) {
            logger.warn { "${repositoryConfig.name}: Unable to check remote changes, will perform pull anyway: ${e.message}" }
            true
        }
    }
    
    fun stop() {
        running = false
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        logger.info { "Stopped periodic puller for ${repositoryConfig.name}" }
    }
}