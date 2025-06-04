package ru.packetdima.datascanner.ui.windows.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.packetdima.datascanner.ui.windows.screens.main.components.MainScreens
import ru.packetdima.datascanner.ui.windows.screens.main.components.SideMenu
import ru.packetdima.datascanner.ui.windows.screens.main.subscreens.FileShareScreen
import ru.packetdima.datascanner.ui.windows.screens.main.subscreens.S3Screen
import ru.packetdima.datascanner.ui.windows.screens.main.tasks.MainScreenTasks


@Composable
fun MainScreen(
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    var scanStateExpanded by remember { mutableStateOf(false) }

    val navController = rememberNavController()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(IntrinsicSize.Min)
                    .padding(horizontal = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                NavHost(
                    navController = navController,
                    startDestination = MainScreens.FileShare.name,
                ) {
                    composable(route = MainScreens.FileShare.name) {
                        FileShareScreen(
                            settingsExpanded = settingsExpanded,
                            expandSettings = {
                                if(scanStateExpanded)
                                    scanStateExpanded = false
                                settingsExpanded = true
                            },
                            hideSettings = {
                                settingsExpanded = false
                            },
                            expandScanState = {
                                if (!scanStateExpanded) {
                                    settingsExpanded = false
                                    scanStateExpanded = true
                                }
                            }
                        )
                    }
                    composable(route = MainScreens.S3.name) {
                        S3Screen(
                            settingsExpanded = settingsExpanded,
                            expandSettings = {
                                if(scanStateExpanded)
                                    scanStateExpanded = false
                                settingsExpanded = true
                            },
                            hideSettings = {
                                settingsExpanded = false
                            },
                            expandScanState = {
                                if (!scanStateExpanded) {
                                    settingsExpanded = false
                                    scanStateExpanded = true
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MainScreenTasks(
                    expanded = scanStateExpanded,
                    onExpandedClick = {
                        if (!scanStateExpanded) {
                            settingsExpanded = false
                        }
                        scanStateExpanded = !scanStateExpanded
                    }
                )
            }


        SideMenu(
            navController,
            modifier = Modifier
                .align(Alignment.CenterEnd)
        )
    }
}

