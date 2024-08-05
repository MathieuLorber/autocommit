package net.mlorber.autocommit.watcher

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import mu.KotlinLogging
import net.mlorber.autocommit.config.RepositoryConfig
import net.mlorber.autocommit.utils.GitUtils

class Watcher {

    companion object {
        const val gitRepository = ".git"
    }

    private val logger = KotlinLogging.logger {}

    private val thread: Thread

    // TODO print message if dir is empty
    constructor(repositoryConfig: RepositoryConfig) {
        thread =
            Thread({
                val watcher = FileSystems.getDefault().newWatchService()
                register(repositoryConfig.path, watcher)
                while (!Thread.currentThread().isInterrupted()) {
                    val key = watcher.take()
                    key.pollEvents().forEach { event ->
                        if (event.context().toString() == gitRepository) {
                            return@forEach
                        }
                        val kind = event.kind()
                        if (kind === OVERFLOW) {
                            return@forEach
                        }
                        GitUtils.saveAndUpdate(repositoryConfig)
                    }
                    // Reset the key -- this step is critical if you want to
                    // receive further watch events.  If the key is no longer valid,
                    // the directory is inaccessible so exit the loop.
                    val valid = key.reset()
                    if (!valid) {
                        break
                    }
                }
            })
        thread.start()
    }

    fun register(dir: Path, watcher: java.nio.file.WatchService) {
        val file = dir.toFile()
        if (file.isDirectory && file.name == gitRepository) {
            return
        }
        logger.debug { "Register $file" }
        if (file.isDirectory) {
            dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            file.let {
                it.listFiles()?.forEach {
                    if (it.isDirectory) {
                        register(it.toPath(), watcher)
                    }
                }
            }
        }
    }

    fun stop() = thread.interrupt()

    fun join() = thread.join()
}
