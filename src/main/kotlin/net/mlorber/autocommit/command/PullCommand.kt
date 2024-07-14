package net.mlorber.autocommit.command

import com.github.ajalt.clikt.core.CliktCommand
import net.mlorber.autocommit.config.Configuration
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class PullCommand : CliktCommand("pull") {
    override fun run() {
        Configuration.repos.forEach {
            // TODO needs auth
            val repository =
                FileRepositoryBuilder().setGitDir(it.path.toFile().resolve(".git")).build()
            val git = Git(repository)
            println(it)
            //            try {
            git.pull().apply {
                remote = "origin"
                remoteBranchName = "main"
                call()
            }
            //            } catch (e: Exception) {
            //                e.printStackTrace()
            //            }
        }
    }
}
