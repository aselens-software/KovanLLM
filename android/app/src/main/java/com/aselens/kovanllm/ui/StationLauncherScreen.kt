package com.aselens.kovanllm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aselens.kovanllm.R
import com.aselens.kovanllm.data.I18n
import com.aselens.kovanllm.data.StationItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationLauncherScreen(
    stationUrl: String,
    onUrlChange: (String) -> Unit,
    recentStations: List<StationItem>,
    isConnecting: Boolean,
    errorMessage: String?,
    language: String,
    onLanguageToggle: () -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onConnect: (String) -> Unit,
    onDeleteRecent: (String) -> Unit,
    onScanQr: () -> Unit = {},
    onVerifyPin: (String) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null && (errorMessage.contains("401") || errorMessage.contains("Unauthorized", ignoreCase = true) || errorMessage.contains("PIN", ignoreCase = true))) {
            showPinDialog = true
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = if (isDark) R.drawable.kovanllm_logo_horizontal_dark else R.drawable.kovanllm_logo_horizontal),
                            contentDescription = "KovanLLM",
                            modifier = Modifier.height(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.primaryContainer)
                                .border(1.dp, colors.outline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "27615",
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    // Theme Toggle
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            tint = colors.onSurface
                        )
                    }

                    // Language Toggle
                    Button(
                        onClick = onLanguageToggle,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = I18n.t("lang_toggle", language),
                            color = colors.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = I18n.t("connect_station", language),
                    color = colors.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = I18n.t("station_desc", language),
                    color = colors.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                // Input Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = I18n.t("station_url_label", language),
                                color = colors.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = onScanQr,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR",
                                    tint = colors.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = I18n.t("scan_qr_btn", language),
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = stationUrl,
                            onValueChange = onUrlChange,
                            placeholder = {
                                Text(
                                    I18n.t("station_placeholder", language),
                                    color = colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.primaryContainer,
                                unfocusedContainerColor = colors.primaryContainer,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline,
                                focusedTextColor = colors.onPrimaryContainer,
                                unfocusedTextColor = colors.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Quick presets chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { onUrlChange("http://10.0.2.2:27615") },
                                label = { Text("Emulator (:27615)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.primaryContainer,
                                    labelColor = colors.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.outline,
                                    enabled = true,
                                    selected = false
                                )
                            )
                            FilterChip(
                                selected = false,
                                onClick = { onUrlChange("http://192.168.1.:27615") },
                                label = { Text("Wi-Fi (:27615)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.primaryContainer,
                                    labelColor = colors.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.outline,
                                    enabled = true,
                                    selected = false
                                )
                            )
                            FilterChip(
                                selected = false,
                                onClick = { showPinDialog = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                label = { Text("PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.primaryContainer,
                                    labelColor = colors.primary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = colors.primary.copy(alpha = 0.5f),
                                    enabled = true,
                                    selected = false
                                )
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    color = colors.error,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { onConnect(stationUrl) },
                            enabled = stationUrl.isNotBlank() && !isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(I18n.t("connecting", language))
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(I18n.t("connect_btn", language), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Recent Stations Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = I18n.t("recent_stations", language),
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${recentStations.size}",
                        color = colors.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            if (recentStations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = I18n.t("no_recent", language),
                            color = colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(recentStations) { station ->
                    RecentStationCard(
                        station = station,
                        openLabel = I18n.t("open", language),
                        onConnect = { onConnect(station.url) },
                        onDelete = { onDeleteRecent(station.url) }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = I18n.t("footer_aselens", language),
                        color = colors.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showPrivacyDialog = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = I18n.t("privacy_policy", language),
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // PIN Dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(I18n.t("pin_dialog_title", language), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = I18n.t("pin_dialog_desc", language),
                            fontSize = 13.sp,
                            color = colors.onSurface.copy(alpha = 0.8f)
                        )
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                    enteredPin = it
                                }
                            },
                            placeholder = { Text(I18n.t("pin_dialog_placeholder", language)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.primaryContainer,
                                unfocusedContainerColor = colors.primaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (enteredPin.length == 6) {
                                showPinDialog = false
                                onVerifyPin(enteredPin)
                            }
                        },
                        enabled = enteredPin.length == 6
                    ) {
                        Text(I18n.t("verify_pin_btn", language), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text(I18n.t("privacy_close", language))
                    }
                }
            )
        }

        // Privacy Policy Dialog
        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(I18n.t("privacy_policy_title", language), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = I18n.t("privacy_policy_body", language),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = colors.onSurface.copy(alpha = 0.85f)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showPrivacyDialog = false }) {
                        Text(I18n.t("privacy_close", language), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun RecentStationCard(
    station: StationItem,
    openLabel: String,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
            .clickable { onConnect() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.secondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = station.name,
                        color = colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = station.url,
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Sil",
                        tint = colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(openLabel, color = colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
