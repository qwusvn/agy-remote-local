package com.example.agyremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agyremote.data.ConnectionConfig
import com.example.agyremote.network.DiscoveredHost
import com.example.agyremote.network.LanScanner
import kotlinx.coroutines.launch

@Composable
fun ConnectionDialog(
    currentConfig: ConnectionConfig,
    onDismiss: () -> Unit,
    onConnect: (ip: String, port: Int, autoReconnect: Boolean, notificationsEnabled: Boolean) -> Unit
) {
    var ipInput by remember { mutableStateOf(currentConfig.hostIp) }
    var portInput by remember { mutableStateOf(currentConfig.port.toString()) }
    var autoReconnect by remember { mutableStateOf(currentConfig.autoReconnect) }
    var notificationsEnabled by remember { mutableStateOf(currentConfig.notificationsEnabled) }

    val scope = rememberCoroutineScope()
    val lanScanner = remember { LanScanner() }
    val discoveredHosts = remember { mutableStateListOf<DiscoveredHost>() }
    var isScanning by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Kết nối Antigravity 2.0")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Nhập địa chỉ IP máy tính đang chạy Antigravity (mặc định cổng 4400):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it.trim() },
                            label = { Text("Địa chỉ IP") },
                            placeholder = { Text("192.168.1.xxx") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.trim() },
                            label = { Text("Cổng") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            if (!isScanning) {
                                isScanning = true
                                discoveredHosts.clear()
                                scope.launch {
                                    lanScanner.scanSubnet { host ->
                                        if (discoveredHosts.none { it.ip == host.ip }) {
                                            discoveredHosts.add(host)
                                        }
                                    }
                                    isScanning = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang quét mạng WiFi...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tự động quét tìm máy trong WiFi")
                        }
                    }
                }

                // Hiển thị danh sách các máy tìm thấy qua quét WiFi
                if (discoveredHosts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Máy chủ tìm thấy trong mạng:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(discoveredHosts) { host ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ipInput = host.ip
                                    portInput = host.port.toString()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${host.ip}:${host.port}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${host.responseTimeMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Danh sách các host đã kết nối trước đây
                if (currentConfig.recentHosts.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lịch sử máy đã kết nối:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(currentConfig.recentHosts.toList()) { hostEntry ->
                        val parts = hostEntry.split(":")
                        val hostIp = parts.getOrNull(0) ?: ""
                        val hostPort = parts.getOrNull(1) ?: "4400"
                        Text(
                            text = hostEntry,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ipInput = hostIp
                                    portInput = hostPort
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Thông báo nền", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Báo rung/chuông khi Agent xong tác vụ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tự động kết nối lại", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Tự reconnect khi mở app hoặc đổi WiFi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = { autoReconnect = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portNumber = portInput.toIntOrNull() ?: 4400
                    onConnect(ipInput, portNumber, autoReconnect, notificationsEnabled)
                },
                enabled = ipInput.isNotBlank()
            ) {
                Text("Kết nối")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
