package ru.packetdima.datascanner.ui.windows.screens.main.subscreens

import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import ru.packetdima.datascanner.common.ScanSettings
import ru.packetdima.datascanner.resources.MainScreen_ScanStartButton
import ru.packetdima.datascanner.resources.MainScreen_SelectPathPlaceholder
import ru.packetdima.datascanner.resources.Res
import ru.packetdima.datascanner.scan.ScanService
import ru.packetdima.datascanner.scan.common.ScanPathHelper
import ru.packetdima.datascanner.scan.common.connectors.ConnectorS3
import ru.packetdima.datascanner.scan.common.files.FileType
import ru.packetdima.datascanner.scan.functions.CertDetectFun
import ru.packetdima.datascanner.scan.functions.CodeDetectFun
import ru.packetdima.datascanner.ui.windows.screens.main.settings.SettingsBox
import ru.packetdima.datascanner.ui.windows.screens.main.settings.SettingsButton

@Composable
fun S3Screen(
    settingsExpanded: Boolean,
    expandSettings: () -> Unit,
    hideSettings: () -> Unit,
    expandScanState: () -> Unit
) {
    val scanService = koinInject<ScanService>()

    val scanSettings = koinInject<ScanSettings>()

    val helperPath by ScanPathHelper.path.collectAsState()
    var path by remember { mutableStateOf(helperPath) }
    var endpoint by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }

    val settingsButtonTransition = updateTransition(settingsExpanded)

    val settingsBoxTransition = updateTransition(settingsExpanded)


    val coroutineScope = rememberCoroutineScope()

    var selectPathError by remember { mutableStateOf(false) }

    var scanNotCorrectPath by remember { mutableStateOf(false) }

    LaunchedEffect(scanNotCorrectPath) {
        if (scanNotCorrectPath) {
            selectPathError = true
            delay(200)
            selectPathError = false
            delay(400)
            selectPathError = true
            delay(200)
            selectPathError = false
            delay(400)
            selectPathError = true
            delay(200)
            selectPathError = false
            scanNotCorrectPath = false
        }
    }

    val focusRequested by ScanPathHelper.focusRequested.collectAsState()

    LaunchedEffect(helperPath) {
        if (helperPath.isNotEmpty()) {
            path = helperPath
            if (focusRequested)
                ScanPathHelper.resetFocus()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .height(60.dp)
                    .width(340.dp),
                value = endpoint,
                onValueChange = { endpoint = it },
                placeholder = { Text(text = "Endpoint") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                modifier = Modifier
                    .height(60.dp)
                    .width(340.dp),
                value = bucket,
                onValueChange = { bucket = it },
                placeholder = { Text(text = "Bucket") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .height(60.dp)
                    .width(340.dp),
                value = accessKey,
                onValueChange = { accessKey = it },
                placeholder = { Text(text = "Access key") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            OutlinedTextField(
                modifier = Modifier
                    .height(60.dp)
                    .width(340.dp),
                value = secretKey,
                onValueChange = { secretKey = it },
                placeholder = { Text(text = "Secret key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium
            )
        }
        OutlinedTextField(
            modifier = Modifier
                .height(60.dp)
                .width(700.dp),
            value = path,
            onValueChange = { path = it },
            placeholder = { Text(text = stringResource(Res.string.MainScreen_SelectPathPlaceholder)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            isError = selectPathError,
            trailingIcon = {
                Row {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(
                                MaterialTheme.shapes.large
                            )
                            .background(MaterialTheme.colorScheme.onBackground)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                //TODO("Not implemented")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.background
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Button(
                    onClick = {

                        if (true) {
                            coroutineScope.launch {
                                val extensions = scanSettings.extensions
                                if (scanSettings.detectCode.value)
                                    extensions.add(FileType.CODE)
                                if (scanSettings.detectCert.value)
                                    extensions.add(FileType.CERT)

                                val detectFunctions =
                                    (scanSettings.detectFunctions + scanSettings.userSignatures)
                                        .toMutableList()
                                if (scanSettings.detectCert.value)
                                    detectFunctions.add(CertDetectFun)
                                if (scanSettings.detectCode.value)
                                    detectFunctions.add(CodeDetectFun)

                                val task = scanService.createTask(
                                    path = path,
                                    extensions = scanSettings.extensions,
                                    detectFunctions = detectFunctions,
                                    fastScan = scanSettings.fastScan.value,
                                    connector = ConnectorS3(
                                        endpointStr = endpoint,
                                        accessKey = accessKey,
                                        secretKey = secretKey,
                                        bucketStr = bucket
                                    )
                                )
                                scanService.startTask(task)
                                expandScanState()

                            }
                        } else {
                            scanNotCorrectPath = true
                        }
                    },
                    modifier = Modifier
                        .width(268.dp)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(0.dp),
                        bottomEnd = CornerSize(0.dp)
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.MainScreen_ScanStartButton),
                        fontSize = 24.sp
                    )
                }
                SettingsButton(
                    transition = settingsButtonTransition,
                    onClick = {
                        if (!settingsExpanded) {
                            expandSettings()
                        } else {
                            hideSettings()
                        }
                    }
                )
            }
            SettingsBox(
                transition = settingsBoxTransition
            )
        }

    }
}