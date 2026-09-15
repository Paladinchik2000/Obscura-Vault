package com.obscura.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VaultPlaceholderScreen(onLock: () -> Unit, onOpenBackup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Хранилище разблокировано")
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenBackup) { Text("Резервная копия") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLock) { Text("Заблокировать") }
    }
}
