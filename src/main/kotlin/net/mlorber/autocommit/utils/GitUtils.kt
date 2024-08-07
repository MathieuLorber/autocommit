package net.mlorber.autocommit.utils

import mu.KotlinLogging
import net.mlorber.autocommit.config.RepositoryConfig
import net.mlorber.autocommit.shell.ShellRunner

object GitUtils {

    private val logger = KotlinLogging.logger {}

    fun saveAndUpdate(repositoryConfig: RepositoryConfig) {
        val currentBranch = currentBranch(repositoryConfig)
        if (currentBranch == repositoryConfig.branch) {
            save(repositoryConfig)
            pull(repositoryConfig)
            push(repositoryConfig)
        } else {
            val diff = diffFiles(repositoryConfig)
            if (diff.isNotEmpty()) {
                logger.warn {
                    "${repositoryConfig.coloredName()} repository is not on '${repositoryConfig.branch}' branch (but '$currentBranch'). Changed files: ${
                        diff.joinToString(
                            separator = ", "
                        )
                    }"
                }
            }
        }
    }

    fun currentBranch(repositoryConfig: RepositoryConfig) =
        ShellRunner.run(repositoryConfig.path, "git branch --show-current").output.first()

    fun diffFiles(repositoryConfig: RepositoryConfig): List<String> {
        ShellRunner.run(repositoryConfig.path, "git add --all")
        return ShellRunner.run(repositoryConfig.path, "git diff --name-status --cached").output
    }

    fun save(repositoryConfig: RepositoryConfig) {
        val diff = diffFiles(repositoryConfig)
        if (diff.isEmpty()) {
            return
        }
        logger.info {
            "Commit ${repositoryConfig.coloredName()} ${diff.joinToString(separator = ", ")}"
        }
        ShellRunner.run(
            repositoryConfig.path,
            "git commit -m '${repositoryConfig.commitMessagePrefix} autocommit'")
    }

    fun pull(repositoryConfig: RepositoryConfig) {
        ShellRunner.run(repositoryConfig.path, "git pull --rebase")
    }

    fun push(repositoryConfig: RepositoryConfig) {
        ShellRunner.run(repositoryConfig.path, "git push")
    }
}
