package ru.packetdima.datascanner.ui.windows.screens.scans.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import ru.packetdima.datascanner.db.models.TaskState
import ru.packetdima.datascanner.resources.Res
import ru.packetdima.datascanner.resources.Task_FoundAttributes
import ru.packetdima.datascanner.resources.aws_s3
import ru.packetdima.datascanner.scan.TaskEntityViewModel
import ru.packetdima.datascanner.scan.TaskFilesViewModel
import ru.packetdima.datascanner.scan.common.connectors.ConnectorS3
import ru.packetdima.datascanner.ui.extensions.color
import ru.packetdima.datascanner.ui.extensions.icon
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanTaskCard(
    taskEntity: TaskEntityViewModel,
    onClick: () -> Unit,
    currentTime: Instant
) {
    val state by taskEntity.state.collectAsState()
    val fastScan by taskEntity.fastScan.collectAsState()
    val path by taskEntity.path.collectAsState()
    val startedAt by taskEntity.startedAt.collectAsState()
    val finishedAt by taskEntity.finishedAt.collectAsState()
    val pausedAt by taskEntity.pausedAt.collectAsState()

    val scanned by taskEntity.scannedFiles.collectAsState()
    val skipped by taskEntity.skippedFiles.collectAsState()
    val selectedFiles by taskEntity.selectedFiles.collectAsState()
    val foundFiles by taskEntity.foundFiles.collectAsState()
    val totalFiles by taskEntity.totalFiles.collectAsState()

    val foundAttributes by taskEntity.foundAttributes.collectAsState()

    val progress = if (selectedFiles > 0) 100 * (scanned + skipped) / selectedFiles else 0

    val folderSize by taskEntity.folderSize.collectAsState()

    val pausedAtInstant = pausedAt?.toInstant(TimeZone.currentSystemDefault())
    val startedAtInstant = startedAt?.toInstant(TimeZone.currentSystemDefault())
    val deltaSeconds by taskEntity.deltaSeconds.collectAsState()

    val deltaDuration = (deltaSeconds ?: 0L).toDuration(DurationUnit.SECONDS)

    val taskFilesViewModel = koinInject<TaskFilesViewModel> { parametersOf(taskEntity.dbTask) }
    val scoreSum by taskFilesViewModel.scoreSum.collectAsState()

    val scanTime = if (startedAt != null) {
        when (state) {
            TaskState.COMPLETED -> finishedAt!!.toInstant(TimeZone.currentSystemDefault()) - startedAtInstant!! - deltaDuration
            TaskState.STOPPED, TaskState.PENDING -> (pausedAtInstant ?: startedAtInstant!!) - startedAtInstant!! - deltaDuration
            else -> currentTime - startedAtInstant!! - deltaDuration
        }
            .toComponents { days, hours, minutes, seconds, _ ->
                if (days > 0)
                    "$days:$hours:${minutes.toString().padStart(2, '0')}" +
                            ":${seconds.toString().padStart(2, '0')}"
                else if (hours > 0)
                    "$hours:${minutes.toString().padStart(2, '0')}" +
                            ":${seconds.toString().padStart(2, '0')}"
                else
                    minutes.toString().padStart(2, '0') +
                            ":${seconds.toString().padStart(2, '0')}"
            }
    } else {
        "00:00:00"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = state.color(),
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                onClick = onClick
            )
            .padding(14.dp),

        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (fastScan) {
                        Icon(
                            imageVector = Icons.Outlined.RocketLaunch,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = state.icon(),
                        contentDescription = null,
                        tint = state.color()
                    )

                    when(taskEntity.dbTask.connector) {
                        is ConnectorS3 -> {
                            Icon(
                                painter = painterResource(Res.drawable.aws_s3),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = path,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                        fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                        letterSpacing = 0.1.sp,
                        style = TextStyle.Default.copy(
                            lineBreak = LineBreak.Heading
                        )
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .width(200.dp)
                ) {
                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ScanTimeStatItem(
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        pausedAt = pausedAt,
                        state = state,
                        progress = progress
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                ScanStat(
                    totalFiles = totalFiles,
                    selectedFiles = selectedFiles,
                    foundFiles = foundFiles,
                    folderSize = folderSize,
                    scanTime = scanTime,
                    scoreSum = scoreSum
                )
            }

            if (foundAttributes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.Task_FoundAttributes),
                        fontSize = 14.sp,
                        letterSpacing = 0.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        foundAttributes.forEach { attr ->
                            AttributeCard(attr)
                        }
                    }
                }
            }
        }
    }
}