package ru.packetdima.datascanner.ui.windows.screens.main.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.jetbrains.compose.resources.painterResource
import ru.packetdima.datascanner.resources.Res
import ru.packetdima.datascanner.resources.aws_s3
import ru.packetdima.datascanner.ui.windows.components.SideMenuItem

@Composable
fun SideMenu(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = MainScreens.valueOf(
        backStackEntry?.destination?.route?.substringBefore("/") ?: MainScreens.FileShare.name
    )

    Surface(
        shape = MaterialTheme.shapes.medium
            .copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .width(88.dp)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .width(IntrinsicSize.Min)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SideMenuItem(
                isSelected = currentScreen == MainScreens.FileShare,
                expanded = false,
                icon = rememberVectorPainter(Icons.Outlined.Folder),
                text = "File share",
                onClick = { navController.navigate(MainScreens.FileShare.name) }
            )
            SideMenuItem(
                isSelected = currentScreen == MainScreens.S3,
                expanded = false,
                icon = painterResource(Res.drawable.aws_s3),
                text = "AWS S3",
                onClick = { navController.navigate(MainScreens.S3.name) }
            )

        }
    }
}