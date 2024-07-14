package net.mlorber.autocommit.watcher

import net.mlorber.autocommit.config.Repository
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW

class Watcher {

    private val thread: Thread

    // TODO print message if dir is empty
    constructor(repository: Repository) {
        thread = Thread({
            val watcher = FileSystems.getDefault().newWatchService()
            register(repository.path, watcher)
            while (!Thread.currentThread().isInterrupted()) {
                val key = watcher.take()
                key.pollEvents().forEach { event ->
                    val kind = event.kind()
                    if (kind === OVERFLOW) {
                        return@forEach
                    }
                    // TODO git save
                    println(event.context())
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
        dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        dir.toFile().let {
            if (it.isDirectory) {
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