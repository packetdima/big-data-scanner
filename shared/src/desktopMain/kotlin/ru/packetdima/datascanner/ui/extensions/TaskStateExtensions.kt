package ru.packetdima.datascanner.ui.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import ru.packetdima.datascanner.db.models.TaskState

@Composable
fun TaskState.color() = when (this) {
    TaskState.LOADING, TaskState.SCANNING, TaskState.SEARCHING -> MaterialTheme.colorScheme.primary
    TaskState.COMPLETED -> MaterialTheme.colorScheme.tertiary
    TaskState.STOPPED, TaskState.PENDING -> MaterialTheme.colorScheme.secondary
    TaskState.FAILED -> MaterialTheme.colorScheme.error
}

@Composable
fun TaskState.icon() = when (this) {
    TaskState.LOADING, TaskState.SCANNING, TaskState.SEARCHING -> Icons.Outlined.PlayArrow
    TaskState.COMPLETED -> Icons.Outlined.DoneAll
    TaskState.STOPPED, TaskState.PENDING -> Icons.Outlined.Pause
    TaskState.FAILED -> Icons.Outlined.Warning
}