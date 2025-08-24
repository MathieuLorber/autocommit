package net.mlorber.autocommit.puller

import mu.KotlinLogging
import net.mlorber.autocommit.config.RepositoryConfig
import net.mlorber.autocommit.shell.ShellRunner

class GitHubRemoteChecker(private val repositoryConfig: RepositoryConfig) {
    private val logger = KotlinLogging.logger {}
    
    fun hasRemoteChanges(): Boolean {
        return checkUsingGitLsRemote()
    }
    
    private fun checkUsingGitLsRemote(): Boolean {
        try {
            ShellRunner.run(repositoryConfig.path, "git fetch --dry-run 2>&1")
            
            val localCommit = ShellRunner.run(
                repositoryConfig.path, 
                "git rev-parse HEAD"
            ).output.firstOrNull()?.trim()
            
            val remoteCommit = ShellRunner.run(
                repositoryConfig.path,
                "git ls-remote origin ${repositoryConfig.branch}"
            ).output.firstOrNull()?.split("\t")?.firstOrNull()?.trim()
            
            if (localCommit == null || remoteCommit == null) {
                logger.debug { "${repositoryConfig.name}: Unable to get commit hashes, assuming changes exist" }
                return true
            }
            
            val hasChanges = localCommit != remoteCommit
            if (hasChanges) {
                logger.debug { "${repositoryConfig.name}: Remote changes detected (local: ${localCommit.take(7)}, remote: ${remoteCommit.take(7)})" }
            }
            
            return hasChanges
        } catch (e: Exception) {
            logger.warn { "${repositoryConfig.name}: Error checking remote: ${e.message}" }
            return true
        }
    }
}