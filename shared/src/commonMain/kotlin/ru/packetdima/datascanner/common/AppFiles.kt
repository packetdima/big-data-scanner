package ru.packetdima.datascanner.common

import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString

@Suppress("Unused")
object AppFiles {
    private val WorkDirPath: Path = when (OS.currentOS()) {
        OS.WINDOWS -> Path(System.getenv("APPDATA")).resolve("BigDataScanner")
        else -> Path(System.getProperty("user.home")).resolve(".ads")
    }

    val UserDirPath: File = when (OS.currentOS()) {
        OS.WINDOWS -> File(System.getenv("USERPROFILE"))
        else -> File(System.getProperty("user.home"))
    }

    val WorkDir = WorkDirPath.toFile().also { path ->
        if (!path.exists() && !path.mkdir())
            throw Exception("Fail to create application directory")
        if (!path.isDirectory)
            throw Exception("Path ${WorkDirPath.absolute()} exists and it's not directory")
        path.absoluteFile
    } ?: throw Exception("Fail to create application directory")

    val LoggingDir: Path = WorkDirPath.resolve("logs")

    val ResultDBFile: File = WorkDirPath.resolve("result.db").toFile()
    val AppSettingsFile: String = WorkDirPath.resolve("AppSettings.json").absolutePathString()
    val ScanSettingsFile: String = WorkDirPath.resolve("ScanSettings.json").absolutePathString()
    val UserSignaturesFiles: String = WorkDirPath.resolve("UserSignatures.json").absolutePathString()
    val Icon: File = WorkDirPath
        .resolve(
            when (OS.currentOS()) {
                OS.WINDOWS -> "Big Data Scanner.ico"
                else -> "Big Data Scanner.png"
            }
        )
        .toFile()
    val MigrationsDirectory: Path = WorkDirPath.resolve("migrations")
}