package net.mlorber.autocommit.config

import java.nio.file.Path

data class Repository(val name: String, val path: Path, val ignoreList: List<String>)
