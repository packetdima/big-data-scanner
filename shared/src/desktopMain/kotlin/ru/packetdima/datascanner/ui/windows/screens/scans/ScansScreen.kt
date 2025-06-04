package ru.packetdima.datascanner.ui.windows.screens.scans

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.koin.compose.koinInject
import ru.packetdima.datascanner.db.models.TaskState
import ru.packetdima.datascanner.scan.ScanService
import ru.packetdima.datascanner.ui.windows.screens.scans.components.ScanFilterChipBox
import ru.packetdima.datascanner.ui.windows.screens.scans.components.ScanTaskCard

@Composable
fun ScansScreen(onTaskClick: (Int) -> Unit) {
    val scanService = koinInject<ScanService>()

    val filterTaskStates = remember { mutableListOf<TaskState>() }
    var active by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    val allTasks by scanService.tasks.tasks.collectAsState()

    val filteredTasks = allTasks.filter { task ->
        if (filterTaskStates.isEmpty())
            task.state.value != TaskState.LOADING
        else
            task.state.value in filterTaskStates
    }.sortedByDescending { it.finishedAt.value }
        .sortedByDescending { it.pausedAt.value }
        .sortedByDescending { it.startedAt.value }

    var currentTime by remember { mutableStateOf(Clock.System.now()) }

    LaunchedEffect(currentTime) {
        while (true) {
            currentTime = Clock.System.now()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 15.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(bottom = 8.dp)
        ) {
            ScanFilterChipBox(
                active = active,
                paused = paused,
                error = error,
                completed = completed,
                onActiveClick = {
                    active = !active
                    if (active) {
                        filterTaskStates.addAll(
                            listOf(
                                TaskState.SCANNING,
                                TaskState.SEARCHING,
                            )
                        )
                    } else {
                        filterTaskStates.removeAll(
                            listOf(
                                TaskState.SCANNING,
                                TaskState.SEARCHING,
                            )
                        )
                    }
                },
                onPausedClick = {
                    paused = !paused
                    if (paused) {
                        filterTaskStates.add(TaskState.STOPPED)
                        filterTaskStates.add(TaskState.PENDING)
                    } else {
                        filterTaskStates.remove(TaskState.STOPPED)
                        filterTaskStates.remove(TaskState.PENDING)
                    }
                },
                onErrorClick = {
                    error = !error
                    if (error) {
                        filterTaskStates.add(TaskState.FAILED)
                    } else {
                        filterTaskStates.remove(TaskState.FAILED)
                    }
                },
                onCompletedClick = {
                    completed = !completed
                    if (completed) {
                        filterTaskStates.add(TaskState.COMPLETED)
                    } else {
                        filterTaskStates.remove(TaskState.COMPLETED)
                    }
                }
            )
        }
        Box {
            val state = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 30.dp),
                state = state,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks) { task ->
                    ScanTaskCard(
                        taskEntity = task,
                        onClick = {
                            onTaskClick(task.id.value!!)
                        },
                        currentTime = currentTime,
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(state),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 10.dp)
                    .width(10.dp)
                    .align(Alignment.CenterEnd),
                style = LocalScrollbarStyle.current.copy(
                    unhoverColor = MaterialTheme.colorScheme.secondary,
                    hoverColor = MaterialTheme.colorScheme.primary
                )
            )
        }

    }
}