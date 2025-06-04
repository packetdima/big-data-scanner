package ru.packetdima.datascanner.scan.common.connectors

import ru.packetdima.datascanner.scan.common.FilesCounter
import java.io.File

interface IConnector {
    suspend fun getFile(filePath: String): File
    suspend fun scanDirectory(
        dir: String,
        extensions: List<String>,
        fileSelected: (file: FoundedFile) -> Unit
    ): FilesCounter
}