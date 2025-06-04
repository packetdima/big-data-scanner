package ru.packetdima.datascanner.ui.windows.screens.scans.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import ru.packetdima.datascanner.db.models.TaskState
import ru.packetdima.datascanner.resources.*
import ru.packetdima.datascanner.ui.windows.components.DateFormat

@Composable
fun ScanTimeStatItem(
    startedAt: LocalDateTime?,
    finishedAt: LocalDateTime?,
    pausedAt: LocalDateTime?,
    state: TaskState,
    progress: Long
) {
    Column {
        Text(
            text = stringResource(
                resource = Res.string.Task_StartedAt,
                startedAt?.let {
                    DateFormat.format(it)
                } ?: ""
            ),
            fontSize = 14.sp,
            letterSpacing = 0.1.sp
        )

        if (finishedAt != null && state == TaskState.COMPLETED) {
            Text(
                text = stringResource(
                    resource = Res.string.Task_FinishedAt,
                    finishedAt.let {
                        DateFormat.format(it)
                    }
                ),
                fontSize = 14.sp,
                letterSpacing = 0.1.sp
            )
        } else if (pausedAt != null && (state == TaskState.STOPPED || state == TaskState.PENDING)) {
            Text(
                text = stringResource(
                    resource = Res.string.Task_PausedAt,
                    pausedAt.let {
                        DateFormat.format(it)
                    }
                ),
                fontSize = 14.sp,
                letterSpacing = 0.1.sp
            )
        } else {
            Text(
                text = stringResource(
                    resource = Res.string.Task_Progress,
                    progress
                ),
                fontSize = 14.sp,
                letterSpacing = 0.1.sp
            )
        }
    }
}