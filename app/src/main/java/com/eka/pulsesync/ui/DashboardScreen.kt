package com.eka.pulsesync.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.eka.pulsesync.ble.BleManager
import com.eka.pulsesync.util.NotificationHelper
import com.eka.pulsesync.data.BandMetrics
import com.eka.pulsesync.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun DashboardScreen(bleManager: BleManager? = null) {
    val context = LocalContext.current
    val bleState by bleManager?.connectionState?.collectAsState() ?: remember { mutableStateOf("DISCONNECTED") }
    val bleBattery by bleManager?.batteryLevel?.collectAsState() ?: remember { mutableStateOf(null) }
    val bleLogs by bleManager?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    var metrics by remember { mutableStateOf(BandMetrics()) }

    // Update with real BLE battery if available
    val activeBattery = bleBattery ?: metrics.battery
    val isConnected = bleState.contains("CONNECTED")

    var logHistory by remember { mutableStateOf(listOf(
        "[READY] PulseSync Native Android BLE Engine initialized",
        "[TARGET] MAC: 04:34:C3:D0:70:D3 (miwear.watch.o66gl)",
        "[AUTH] Token AES-128 configured",
        "[SOCKET] TCP bridge ready: 192.168.1.15:8080"
    )) }

    // Combine simulated logs with real BLE logs
    val combinedLogs = (bleLogs + logHistory).take(7)

    val hasPerms = remember(context) { NotificationHelper.hasPermissions(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TankaBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HEADER
        HeaderBar(metrics = metrics)

        // PERMISSION WARNING BANNER
        if (!hasPerms) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TankaAmber, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = TankaCardVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ IZIN HP BELUM DIAKTIFKAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TankaAmber
                    )
                    Text(
                        text = "Aplikasi belum dapat izin 'Perangkat di Sekitar' atau 'Notifikasi' dari sistem Android HP. Klik tombol di bawah untuk mengizinkan.",
                        fontSize = 11.sp,
                        color = TankaTextMain
                    )
                    Button(
                        onClick = { NotificationHelper.openAppSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = TankaAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Buka Pengaturan Izin HP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // HERO METRIC: HEART RATE WITH ANIMATED WAVE
        HeartRateHeroCard(metrics = metrics)

        // PULSESYNC AI BIOMETRIC ENGINE CARD
        AiHealthCoachCard(metrics = metrics)

        // BENTO METRICS GRID (SPO2, BATTERY, STEPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricSubCard(
                modifier = Modifier.weight(1f),
                title = "O2 SATURATION",
                value = if (metrics.spo2 <= 0) "--" else "${metrics.spo2}%",
                tag = if (metrics.spo2 <= 0) "DISCONNECTED" else "NORMAL",
                accentColor = TankaCyan
            )
            MetricSubCard(
                modifier = Modifier.weight(1f),
                title = "BATTERY",
                value = if (activeBattery <= 0) "--" else "$activeBattery%",
                tag = if (bleBattery != null) "LIVE BLE" else "DISCONNECTED",
                accentColor = TankaEmerald
            )
            MetricSubCard(
                modifier = Modifier.weight(1f),
                title = "STEPS",
                value = if (metrics.steps <= 0) "--" else "${metrics.steps}",
                tag = if (metrics.steps <= 0) "DISCONNECTED" else "TODAY",
                accentColor = TankaAmber
            )
        }

        // BIOMECHANICS & RUNNING FORM (PEBBLE MODE)
        BiomechanicsCard(metrics = metrics)

        // SMARTBAND HARDWARE PAIRING CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = TankaCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PAIRED HARDWARE CREDENTIALS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaTextMuted
                    )
                    Text(
                        text = "AES-128 READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaEmerald
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "DEVICE MAC", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                        Text(text = metrics.macAddress, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "MODEL", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                        Text(text = metrics.model, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TankaCyan)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "AUTH TOKEN", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                    Text(
                        text = "${metrics.authKeyHex.take(8)}••••••••••••••••${metrics.authKeyHex.takeLast(4)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TankaEmerald
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            bleManager?.startScanAndConnect(metrics.macAddress)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) TankaCardVariant else TankaCyan
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isConnected) "STATUS: $bleState" else "📡 CONNECT GATT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) TankaEmerald else Color.Black
                        )
                    }

                    if (isConnected) {
                        Button(
                            onClick = {
                                bleManager?.readBattery()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TankaEmerald),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Baterai",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // SMARTBAND NOTIFICATION PUSH TEST CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = TankaCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            var notifTitle by remember { mutableStateOf("Hermes Agent") }
            var notifBody by remember { mutableStateOf("Halo Eka! Test getar dari HP.") }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEST PUSH NOTIFIKASI KE JAM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaTextMuted
                    )
                    Text(
                        text = "GATT 0x2A46",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TankaCyan
                    )
                }

                OutlinedTextField(
                    value = notifTitle,
                    onValueChange = { notifTitle = it },
                    label = { Text("Judul Notifikasi", fontSize = 11.sp, color = TankaTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TankaCyan,
                        unfocusedBorderColor = TankaBorder,
                        focusedContainerColor = TankaCardVariant,
                        unfocusedContainerColor = TankaCardVariant
                    )
                )

                OutlinedTextField(
                    value = notifBody,
                    onValueChange = { notifBody = it },
                    label = { Text("Isi Pesan Notifikasi", fontSize = 11.sp, color = TankaTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TankaCyan,
                        unfocusedBorderColor = TankaBorder,
                        focusedContainerColor = TankaCardVariant,
                        unfocusedContainerColor = TankaCardVariant
                    )
                )

                Button(
                    onClick = {
                        val result = NotificationHelper.postNotification(context, notifTitle, notifBody)
                        bleManager?.sendVibrateAlert()
                        Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                        val frame = if (result.first) {
                            "[TX NOTIF] OK: \"$notifTitle: $notifBody\""
                        } else {
                            "[TX ERR] ${result.second}"
                        }
                        logHistory = (listOf(frame) + logHistory).take(6)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TankaEmerald),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚡ Kirim Notifikasi & Getarkan Jam",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // LINUX WORKSTATION BRIDGE CARD
        LinuxBridgeCard(
            metrics = metrics,
            onToggleSync = {
                metrics = metrics.copy(isLinuxSyncing = !metrics.isLinuxSyncing)
            }
        )

        // REALTIME PACKET TERMINAL
        TerminalLogsCard(logs = combinedLogs)
    }
}

@Composable
fun HeaderBar(metrics: BandMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PULSE // SYNC",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = metrics.deviceName,
                fontSize = 12.sp,
                color = TankaTextMuted
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = if (metrics.isBandConnected) "BLE ON" else "BLE OFF",
                color = if (metrics.isBandConnected) TankaEmerald else TankaRed
            )
            StatusPill(
                label = if (metrics.isLinuxSyncing) "LINUX 100Hz" else "DISCONNECTED",
                color = if (metrics.isLinuxSyncing) TankaCyan else TankaTextMuted
            )
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
fun HeartRateHeroCard(metrics: BandMetrics) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HEART RATE MONITOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TankaTextMuted
                )
                Text(
                    text = "GATT 0x2A37",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TankaCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (metrics.heartRate <= 0) "--" else "${metrics.heartRate}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = TankaRed,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas live pulse curve
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TankaCardVariant)
            ) {
                val path = Path()
                val stepX = size.width / 10f
                val midY = size.height / 2f

                path.moveTo(0f, midY)
                path.lineTo(stepX * 2, midY)
                path.lineTo(stepX * 3, midY - 6f)
                path.lineTo(stepX * 4, midY + 12f)
                path.lineTo(stepX * 5, midY - (34f * pulseScale))
                path.lineTo(stepX * 6, midY + (24f * pulseScale))
                path.lineTo(stepX * 7, midY - 8f)
                path.lineTo(stepX * 8, midY)
                path.lineTo(size.width, midY)

                drawPath(
                    path = path,
                    color = TankaCyan,
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}

@Composable
fun MetricSubCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    tag: String,
    accentColor: Color
) {
    Card(
        modifier = modifier
            .border(1.dp, TankaBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TankaTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tag,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = accentColor
            )
        }
    }
}

@Composable
fun LinuxBridgeCard(
    metrics: BandMetrics,
    onToggleSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LINUX ARCH WORKSTATION BRIDGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TankaTextMuted
                )
                Text(
                    text = if (metrics.isLinuxSyncing) "ACTIVE" else "PAUSED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (metrics.isLinuxSyncing) TankaEmerald else TankaAmber
                )
            }

            Text(
                text = "Target Socket: ${metrics.linuxTarget} (Arch Linux Zen / Hyprland Waybar)",
                fontSize = 11.sp,
                color = TankaTextMain
            )

            Button(
                onClick = onToggleSync,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (metrics.isLinuxSyncing) TankaCardVariant else TankaCyan
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (metrics.isLinuxSyncing) "Pause Linux Stream" else "Resume Linux Stream",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (metrics.isLinuxSyncing) TankaTextMuted else Color.Black
                )
            }
        }
    }
}

@Composable
fun TerminalLogsCard(logs: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "REALTIME PROTOCOL TELEMETRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TankaTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070A09))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                logs.forEach { log ->
                    Text(
                        text = log,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            log.contains("AUTH") -> TankaEmerald
                            log.contains("STREAM") -> TankaCyan
                            else -> TankaTextMuted
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AiHealthCoachCard(metrics: BandMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TankaCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "✦", color = TankaCyan, fontSize = 14.sp)
                    Text(
                        text = "PULSESYNC AI // BIOMETRIC COACH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
                StatusPill(
                    label = metrics.aiCognitiveStatus,
                    color = TankaEmerald
                )
            }

            Text(
                text = metrics.aiRecommendation,
                fontSize = 11.5.sp,
                color = TankaTextMain,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TankaCardVariant)
                        .padding(8.dp)
                ) {
                    Text(text = "STRESS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                    Text(
                        text = if (metrics.stressLevel > 0) "${metrics.stressLevel}/100" else "24 (LOW)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaCyan
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TankaCardVariant)
                        .padding(8.dp)
                ) {
                    Text(text = "ENERGY", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                    Text(
                        text = if (metrics.bodyEnergy > 0) "${metrics.bodyEnergy}%" else "88%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaEmerald
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TankaCardVariant)
                        .padding(8.dp)
                ) {
                    Text(text = "NET BURN", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TankaTextMuted)
                    Text(
                        text = "${metrics.aiCaloricNetDeficit} kkal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TankaAmber
                    )
                }
            }
        }
    }
}

@Composable
fun BiomechanicsCard(metrics: BandMetrics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TankaBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = TankaCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "6-AXIS IMU BIOMECHANICS (PEBBLE MODE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TankaTextMuted
                )
                Text(
                    text = "KINETIC ENGINE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TankaCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricSubCard(
                    modifier = Modifier.weight(1f),
                    title = "CONTACT TIME",
                    value = "214 ms",
                    tag = "OPTIMAL",
                    accentColor = TankaEmerald
                )
                MetricSubCard(
                    modifier = Modifier.weight(1f),
                    title = "IMPACT FORCE",
                    value = "1.8 G",
                    tag = "SAFE",
                    accentColor = TankaCyan
                )
                MetricSubCard(
                    modifier = Modifier.weight(1f),
                    title = "CADENCE",
                    value = "174 SPM",
                    tag = "RHYTHM",
                    accentColor = TankaAmber
                )
            }
        }
    }
}
