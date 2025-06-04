package ru.packetdima.datascanner.ui.windows.screens.main.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import ru.packetdima.datascanner.db.models.TaskState
import ru.packetdima.datascanner.resources.Res
import ru.packetdima.datascanner.resources.aws_s3
import ru.packetdima.datascanner.scan.ScanService
import ru.packetdima.datascanner.scan.TaskEntityViewModel
import ru.packetdima.datascanner.scan.common.connectors.ConnectorS3
import ru.packetdima.datascanner.ui.extensions.color
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun MainScreenTaskCard(taskEntity: TaskEntityViewModel, currentTime: Instant) {
    val scanService = koinInject<ScanService>()
    val state by taskEntity.state.collectAsState()
    val pausedAt by taskEntity.pausedAt.collectAsState()
    val startedAt by taskEntity.startedAt.collectAsState()
    val pausedAtInstant = pausedAt?.toInstant(TimeZone.currentSystemDefault())
    val startedAtInstant = startedAt?.toInstant(TimeZone.currentSystemDefault())
    val deltaSeconds by taskEntity.deltaSeconds.collectAsState()
    val busy by taskEntity.busy.collectAsState()

    val deltaDuration = (deltaSeconds?: 0L).toDuration(DurationUnit.SECONDS)

    val scanTime = if (startedAt != null) {
        (if (pausedAtInstant != null && startedAtInstant != null)
            pausedAtInstant - startedAtInstant - deltaDuration
        else
            currentTime - startedAtInstant!! - deltaDuration)
            .toComponents { days, hours, minutes, seconds, _ ->
                if(days > 0)
                    "$days:$hours:${minutes.toString().padStart(2, '0')}" +
                            ":${seconds.toString().padStart(2, '0')}"
                else if(hours > 0)
                    "$hours:${minutes.toString().padStart(2, '0')}" +
                            ":${seconds.toString().padStart(2, '0')}"
                else
                    minutes.toString().padStart(2, '0') +
                            ":${seconds.toString().padStart(2, '0')}"
            }
    } else {
        "00:00:00"
    }

    val scanned by taskEntity.scannedFiles.collectAsState()
    val skipped by taskEntity.skippedFiles.collectAsState()
    val allFiles by taskEntity.selectedFiles.collectAsState()

    val progress = if (allFiles > 0)
        (scanned + skipped).toFloat() / allFiles
    else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                state.color().copy(alpha = 0.25f)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(state.color())
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when(taskEntity.dbTask.connector) {
                is ConnectorS3 -> {
                    Icon(
                        painter = painterResource(Res.drawable.aws_s3),
                        contentDescription = "AWS S3",
                        modifier = Modifier
                            .size(32.dp)
                    )
                }
            }
            Text(
                modifier = Modifier
                    .weight(0.5f),
                text = taskEntity.dbTask.path,
            )
            Text(text = scanTime)
            Row(
                modifier = Modifier
                    .width(100.dp)
                    .padding(start = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state != TaskState.COMPLETED) {
                    if(busy || state in setOf(TaskState.SEARCHING, TaskState.LOADING)) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(40.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface.copy(0.5f))
                                .clickable {
                                    when (state) {
                                        TaskState.SCANNING ->
                                            scanService.stopTask(taskEntity)

                                        TaskState.STOPPED -> scanService.resumeTask(taskEntity)
                                        TaskState.PENDING -> scanService.startTask(taskEntity)
                                        else -> scanService.rescanTask(taskEntity)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (state) {
                                    TaskState.SEARCHING, TaskState.SCANNING, TaskState.LOADING -> Icons.Outlined.Pause
                                    TaskState.STOPPED, TaskState.PENDING -> Icons.Outlined.PlayArrow
                                    else -> Icons.Outlined.RestartAlt
                                },
                                contentDescription = null
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface.copy(0.5f))
                        .clickable {
                            runBlocking { scanService.deleteTask(taskEntity) }

                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null
                    )
                }
            }
        }
    }
}