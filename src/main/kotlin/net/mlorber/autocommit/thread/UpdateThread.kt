package net.mlorber.autocommit.thread

import kotlin.io.path.exists
import mu.KotlinLogging
import net.mlorber.autocommit.config.RepositoryConfig
import net.mlorber.autocommit.utils.GitUtils

class UpdateThread {

    private val logger = KotlinLogging.logger {}

    private val thread: Thread

    // TODO print message if dir is empty
    constructor(repositoryConfig: RepositoryConfig) {
        thread =
            Thread({
                if (!repositoryConfig.path.exists()) {
                    logger.error { "Repository path does not exist: ${repositoryConfig.path}" }
                    return@Thread
                }
                GitUtils.saveAndUpdate(repositoryConfig)
            })
        thread.start()
    }
}
