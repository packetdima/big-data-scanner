package ru.packetdima.datascanner.scan

import MigrationUtils
import info.downdetector.bigdatascanner.common.IDetectFunction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.packetdima.datascanner.common.AppFiles
import ru.packetdima.datascanner.common.AppSettings
import ru.packetdima.datascanner.common.LogMarkers
import ru.packetdima.datascanner.common.ScanSettings
import ru.packetdima.datascanner.db.DatabaseConnector
import ru.packetdima.datascanner.db.models.*
import ru.packetdima.datascanner.scan.common.connectors.ConnectorS3
import ru.packetdima.datascanner.scan.common.connectors.IConnector
import ru.packetdima.datascanner.scan.common.files.FileType
import ru.packetdima.datascanner.scan.functions.CertDetectFun
import ru.packetdima.datascanner.scan.functions.CodeDetectFun
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalDatabaseMigrationApi::class)
class ScanService : KoinComponent {
    private val database: DatabaseConnector by inject()

    private val appSettings: AppSettings by inject()
    private val scanSettings: ScanSettings by inject()

    val tasks: TasksViewModel by inject()

    private var scanThreads: Array<ScanThread>

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    val changingThreadsCount = AtomicBoolean(false)

    private var migrationRequired = false

    init {
        transaction(database.connection) {
            SchemaUtils.create(
                Tasks,
                TaskFiles,
                TaskFileExtensions,
                TaskDetectFunctions,
                TaskFileScanResults
            )

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(Tasks, withLogs = false)
            if (statements.isNotEmpty()) {
                logger.info {
                    "Database migration required."
                }
                migrationRequired = true
                if (!File(AppFiles.MigrationsDirectory.absolutePathString()).exists()) {
                    File(AppFiles.MigrationsDirectory.absolutePathString()).mkdir()
                }
                if (!File(
                        AppFiles
                            .MigrationsDirectory
                            .resolve("V2__AddMissingColumns.sql")
                            .absolutePathString()
                    )
                        .exists()
                ) {
                    MigrationUtils.generateMigrationScript(
                        Tasks,
                        scriptDirectory = AppFiles.MigrationsDirectory.absolutePathString(),
                        scriptName = "V2__AddMissingColumns",
                    )
                } else {
                    MigrationUtils.generateMigrationScript(
                        Tasks,
                        scriptDirectory = AppFiles.MigrationsDirectory.absolutePathString(),
                        scriptName = "V3__AddConnectors",
                    )
                }

            }
        }

        if (migrationRequired) {
            val flyway = Flyway.configure()
                .dataSource(database.dbSettings.url, "", "")
                .defaultSchema("main")
                .schemas("main")
                .locations("filesystem:${AppFiles.MigrationsDirectory}")
                .baselineOnMigrate(appSettings.firstMigration.value)
                .load()


            val m = runBlocking {
                database.transaction {
                    flyway.migrate()
                }
            }

            if (m.success && m.successfulMigrations.isNotEmpty()) {
                appSettings.firstMigration.value = false
                appSettings.save()
                migrationRequired = false
                logger.info {
                    "Database migration completed."
                }
            } else {
                logger.error {
                    "Database migration failed."
                }
            }
        }

        scanThreads = Array(appSettings.threadCount.value) { ScanThread() }
        CoroutineScope(Dispatchers.IO).launch {
            database.transaction {
                Task.all().forEach { task ->

                    val taskEntity = TaskEntityViewModel(
                        dbTask = task,
                        state = task.taskState,
                        totalFiles = task.filesCount,
                        foundAttributes = (TaskFileScanResults innerJoin TaskFiles innerJoin TaskDetectFunctions)
                            .select(TaskDetectFunctions.function)
                            .where { TaskFiles.task.eq(task.id) }
                            .withDistinct()
                            .map { it[TaskDetectFunctions.function] }
                            .toSet(),
                        foundFiles = TaskFiles
                            .innerJoin(TaskFileScanResults)
                            .select(TaskFiles.id)
                            .where { TaskFiles.task.eq(task.id) }
                            .withDistinct()
                            .count(),
                        folderSize = task.size
                    )
                    if (task.taskState == TaskState.SCANNING) {
                        task.pauseDate = task.lastFileDate

                        taskEntity.setState(TaskState.STOPPED)
                        TaskFiles.update(
                            where = {
                                TaskFiles.task.eq(task.id) and
                                        TaskFiles.state.neq(TaskState.STOPPED) and
                                        TaskFiles.state.neq(TaskState.COMPLETED) and
                                        TaskFiles.state.neq(TaskState.FAILED)
                            }
                        ) {
                            it[state] = TaskState.STOPPED
                        }

                        logger.info(throwable = null, LogMarkers.UserAction) {
                            "Stopped task after restart (${taskEntity.id.value}) ${taskEntity.path.value}"
                        }
                    }

                    if(task.taskState == TaskState.SEARCHING) {
                        task.pauseDate = task.startedAt

                        taskEntity.setState(TaskState.PENDING)
                        TaskFiles.deleteWhere {
                            TaskFiles.task.eq(task.id)
                        }

                        logger.info(throwable = null, LogMarkers.UserAction) {
                            "Reset task after restart (${taskEntity.id.value}) ${taskEntity.path.value}"
                        }
                    }

                    tasks.add(taskEntity)
                }
            }
        }
    }

    fun start() {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Starting scan threads"
        }
        coroutineScope.launch {
            while (changingThreadsCount.get())
                delay(1000)

            scanThreads.forEach {
                if (!it.started)
                    it.start()
            }
        }

    }

    suspend fun stop() {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Stopping scan threads"
        }
        coroutineScope {
            scanThreads.map {
                async {
                    if (it.started)
                        it.stop()
                }
            }.awaitAll()
        }
    }

    fun setThreadsCount() {
        val scanStarted = scanThreads.any { it.started }
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Setting scan threads to ${appSettings.threadCount.value}"
        }
        coroutineScope.launch {
            changingThreadsCount.set(true)
            if (scanStarted)
                stop()

            scanThreads = Array(appSettings.threadCount.value) { ScanThread() }
            if (scanStarted)
                start()
            changingThreadsCount.set(false)
        }
    }

    suspend fun createTask(
        path: String,
        extensions: List<FileType>? = null,
        detectFunctions: List<IDetectFunction>? = null,
        fastScan: Boolean? = null,
        connector: IConnector
    ): TaskEntityViewModel {
        return database.transaction {
            val task = Task.new {
                this.path = path
                this.taskState = TaskState.PENDING
                this.fastScan = fastScan ?: scanSettings.fastScan.value
                this.connector = connector
            }
            logger.info(throwable = null, LogMarkers.UserAction) {
                "Creating task. " +
                        "ID: ${task.id.value}. " +
                        "Path: \"$path\". " +
                        "Extensions: ${
                            (if (extensions != null)
                                extensions else
                                scanSettings.extensions).joinToString { it.name }
                        }. " +
                        "Detect functions: ${
                            (if (detectFunctions != null)
                                detectFunctions
                            else (scanSettings.detectFunctions
                                    + scanSettings.userSignatures
                                    + (if (scanSettings.detectCert.value)
                                listOf(CertDetectFun) else listOf())
                                    + (if (scanSettings.detectCode.value)
                                listOf(CodeDetectFun) else listOf())
                                    )
                                    ).joinToString { it.name }
                        }. " +
                        "Fast scan: ${fastScan ?: scanSettings.fastScan.value}. " +
                        "Threads: ${appSettings.threadCount.value}. " +
                        "Connector: $connector ." +
                        if (connector is ConnectorS3) {
                            "Endpoind: ${connector.endpointStr}. " +
                                    "Bucket: ${connector.bucketStr}. " +
                                    "Region: ${connector.regionStr}. "
                        } else ""
            }
            if (extensions != null) {
                extensions.forEach { ext ->
                    TaskFileExtension.new {
                        this.task = task
                        this.extension = ext
                    }
                }
            } else {
                scanSettings.extensions.forEach { ext ->
                    TaskFileExtension.new {
                        this.task = task
                        this.extension = ext
                    }
                }
                if (scanSettings.detectCert.value) {
                    TaskFileExtension.new {
                        this.task = task
                        this.extension = FileType.CERT
                    }
                }
                if (scanSettings.detectCode.value) {
                    TaskFileExtension.new {
                        this.task = task
                        this.extension = FileType.CODE
                    }
                }
            }

            if (detectFunctions != null) {
                detectFunctions.forEach { df ->
                    TaskDetectFunction.new {
                        this.task = task
                        this.function = df
                    }
                }
            } else {
                scanSettings.detectFunctions.forEach { df ->
                    TaskDetectFunction.new {
                        this.task = task
                        this.function = df
                    }
                }
                scanSettings.userSignatures.forEach { df ->
                    TaskDetectFunction.new {
                        this.task = task
                        this.function = df
                    }
                }
                if (scanSettings.detectCert.value) {
                    TaskDetectFunction.new {
                        this.task = task
                        this.function = CertDetectFun
                    }
                }
                if (scanSettings.detectCode.value) {
                    TaskDetectFunction.new {
                        this.task = task
                        this.function = CertDetectFun
                    }
                }
            }

            val taskEntity = TaskEntityViewModel(task)
            tasks.add(taskEntity)
            taskEntity
        }
    }

    suspend fun deleteTask(task: TaskEntityViewModel) {
        task.stop()
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Delete task. ID: ${task.id.value}. Path: \"${task.path.value}\""
        }
        database.transaction {
            TaskFileExtensions.deleteWhere {
                TaskFileExtensions.task.eq(task.dbTask.id)
            }
            TaskDetectFunctions.deleteWhere {
                TaskDetectFunctions.task.eq(task.dbTask.id)
            }
            TaskFiles.deleteWhere {
                TaskFiles.task.eq(task.dbTask.id)
            }
            task.dbTask.delete()
        }
        tasks.delete(task)
    }

    fun startTask(task: TaskEntityViewModel) {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Starting task. ID: ${task.id.value}. Path: \"${task.path.value}\""
        }
        task.start {
            this.start()
        }
    }

    fun stopTask(task: TaskEntityViewModel) {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Stopping task. ID: ${task.id.value}. Path: \"${task.path.value}\""
        }
        task.stop()
    }

    fun resumeTask(task: TaskEntityViewModel) {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Resume task. ID: ${task.id.value}. Path: \"${task.path.value}\""
        }
        task.resume {
            this.start()
        }
    }

    fun rescanTask(task: TaskEntityViewModel) {
        logger.info(throwable = null, LogMarkers.UserAction) {
            "Restart task. ID: ${task.id.value}. Path: \"${task.path.value}\""
        }
        task.rescan {
            this.start()
        }
    }
}