package net.mlorber.autocommit.shell

import java.io.InputStream
import java.nio.file.Path
import java.util.Scanner
import mu.KotlinLogging

object ShellRunner {

    data class CommandResult(
        val result: Int,
        val output: List<String>,
        val errorOutput: List<String>
    )

    private val logger = KotlinLogging.logger {}

    fun run(command: String, vararg params: String): CommandResult = doRun(null, command, *params)

    fun run(directory: Path, command: String, vararg params: String): CommandResult =
        doRun(directory, command, *params)

    private fun doRun(directory: Path?, command: String, vararg params: String): CommandResult {
        val builder =
            ProcessBuilder().apply {
                environment().apply {
                    val addToPath = listOf("/usr/local/bin", "/opt/homebrew/bin")
                    put("PATH", "${get("PATH")}:${addToPath.joinToString(separator = ":")}")
                }
                if (directory != null) {
                    directory(directory.toFile())
                }
            }
        val fullCommand = command + params.fold("") { acc, s -> "$acc $s" }
        logger.debug { "Run '$fullCommand' ${if (directory != null) "in $directory" else ""}" }
        builder.command("sh", "-c", fullCommand)
        val process = builder.start()
        val (output, outputThread) = outputThread(process.inputStream, "Command output:")
        val (error, errorThread) = outputThread(process.errorStream, "Command error:")
        val result = process.waitFor()
        outputThread.join()
        errorThread.join()
        logger.debug { "Command result: $result" }
        return CommandResult(result, output, error)
    }

    private fun outputThread(
        inputStream: InputStream,
        logPrefix: String
    ): Pair<List<String>, Thread> {
        val result = mutableListOf<String>()
        val t =
            Thread {
                    val s = Scanner(inputStream)
                    while (s.hasNextLine()) {
                        val l = s.nextLine()
                        logger.debug { "$logPrefix $l" }
                        result.add(l)
                    }
                }
                .apply { start() }
        return result to t
    }
}
