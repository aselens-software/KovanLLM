package com.aselens.kovanllm.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aselens.kovanllm.data.EngineConfig
import com.aselens.kovanllm.data.GLOBAL_PRESETS
import com.aselens.kovanllm.data.I18n
import com.aselens.kovanllm.data.ModelPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    stationUrl: String,
    models: List<String>,
    engineConfig: EngineConfig,
    onConfigChange: (EngineConfig) -> Unit,
    language: String,
    onLanguageToggle: () -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onProbeEngine: (Int) -> Unit,
    probeResultText: String?,
    onUnloadModel: () -> Unit,
    onApplyAndReloadModel: () -> Unit = {},
    onStartChat: () -> Unit,
    onDisconnect: () -> Unit
) {
    var expandedModelDropdown by remember { mutableStateOf(false) }
    var enginePortText by remember { mutableStateOf(engineConfig.enginePort.toString()) }
    var isCustomContext by remember { mutableStateOf(engineConfig.customContextLength != null) }
    var customContextText by remember { mutableStateOf((engineConfig.customContextLength ?: engineConfig.contextLength).toString()) }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = I18n.t("settings_title", language),
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.onSurface
                        )
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
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = I18n.t("lang_toggle", language),
                            color = colors.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDisconnect) {
                        Text(
                            I18n.t("disconnect", language),
                            color = colors.error,
                            fontSize = 11.sp
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ultra-Compact Station Connection Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.secondary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = I18n.t("station_active", language),
                            color = colors.secondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${stationUrl.replace("http://", "").replace("https://", "").take(22)}",
                            color = colors.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${models.size} Models",
                            color = colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Target LLM Engine Port Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = I18n.t("engine_port_title", language),
                            color = colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = enginePortText,
                                onValueChange = {
                                    enginePortText = it
                                    val p = it.toIntOrNull()
                                    if (p != null) {
                                        onConfigChange(engineConfig.copy(enginePort = p))
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
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

                            Button(
                                onClick = {
                                    val p = enginePortText.toIntOrNull() ?: 58080
                                    onProbeEngine(p)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(I18n.t("probe_btn", language), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val portPresets = listOf("58080", "1234", "11434", "8080")
                            portPresets.forEach { prt ->
                                FilterChip(
                                    selected = enginePortText == prt,
                                    onClick = {
                                        enginePortText = prt
                                        onConfigChange(engineConfig.copy(enginePort = prt.toInt()))
                                        onProbeEngine(prt.toInt())
                                    },
                                    label = { Text(prt, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.primaryContainer,
                                        labelColor = colors.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = colors.outline,
                                        enabled = true,
                                        selected = enginePortText == prt
                                    )
                                )
                            }
                        }

                        if (!probeResultText.isNullOrBlank()) {
                            Text(
                                text = probeResultText,
                                color = if (probeResultText.contains("Online") || probeResultText.contains("Çevrimiçi")) colors.secondary else colors.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Model Selection
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = I18n.t("active_model_label", language),
                        color = colors.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedModelDropdown,
                        onExpandedChange = { expandedModelDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = engineConfig.activeModel.ifEmpty { models.firstOrNull() ?: "qwen2.5-3b-instruct-abliterated" },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline,
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = expandedModelDropdown,
                            onDismissRequest = { expandedModelDropdown = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            models.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName, color = colors.onSurface, fontSize = 12.sp) },
                                    onClick = {
                                        onConfigChange(engineConfig.copy(activeModel = modelName))
                                        expandedModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Hardware Controls (Graduated GPU Offload & CPU Threads)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            text = "HARDWARE & RESOURCE CONTROLS",
                            color = colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Graduated GPU Offload Slider (0 to 100%)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = I18n.t("gpu_offload_label", language), color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            val gpuText = when (engineConfig.gpuOffloadPercent) {
                                0 -> "0% (CPU Only)"
                                100 -> "100% (Full GPU)"
                                else -> "${engineConfig.gpuOffloadPercent}% (${engineConfig.gpuOffload})"
                            }
                            Text(
                                text = gpuText,
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = engineConfig.gpuOffloadPercent.toFloat(),
                            onValueChange = { percent ->
                                val p = percent.toInt()
                                val offloadStr = when (p) {
                                    0 -> "off"
                                    100 -> "max"
                                    else -> String.format(java.util.Locale.US, "%.2f", p / 100f)
                                }
                                onConfigChange(engineConfig.copy(gpuOffloadPercent = p, gpuOffload = offloadStr))
                            },
                            valueRange = 0f..100f,
                            steps = 99,
                            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                        )

                        // Quick GPU offload presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair("Max", 100),
                                Pair("94% (~30/32)", 94),
                                Pair("90%", 90),
                                Pair("75%", 75),
                                Pair("CPU", 0)
                            ).forEach { (label, pct) ->
                                val isSelected = engineConfig.gpuOffloadPercent == pct
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colors.primary else colors.primaryContainer)
                                        .clickable {
                                            val offloadStr = when (pct) {
                                                0 -> "off"
                                                100 -> "max"
                                                else -> String.format(java.util.Locale.US, "%.2f", pct / 100f)
                                            }
                                            onConfigChange(engineConfig.copy(gpuOffloadPercent = pct, gpuOffload = offloadStr))
                                        }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else colors.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // CPU Threads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = I18n.t("cpu_threads_label", language), color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${engineConfig.cpuThreads} Threads",
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = engineConfig.cpuThreads.toFloat(),
                            onValueChange = { onConfigChange(engineConfig.copy(cpuThreads = it.toInt())) },
                            valueRange = 1f..16f,
                            steps = 15,
                            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                        )

                        // Context Length with Custom option
                        Text(text = I18n.t("context_length_label", language), color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val lengths = listOf(2048, 4096, 8192, 16384, 32768)
                            lengths.forEach { len ->
                                val isSelected = !isCustomContext && engineConfig.contextLength == len
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        isCustomContext = false
                                        onConfigChange(engineConfig.copy(contextLength = len, customContextLength = null))
                                    },
                                    label = { Text(if (len >= 1024) "${len/1024}K" else "$len", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.primaryContainer,
                                        labelColor = colors.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (isSelected) colors.primary else colors.outline,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }

                            // Custom Button
                            FilterChip(
                                selected = isCustomContext,
                                onClick = {
                                    isCustomContext = true
                                    val customVal = customContextText.toIntOrNull() ?: 4096
                                    onConfigChange(engineConfig.copy(contextLength = customVal, customContextLength = customVal))
                                },
                                label = { Text(I18n.t("custom_ctx_label", language), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.primaryContainer,
                                    labelColor = colors.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isCustomContext) colors.primary else colors.outline,
                                    enabled = true,
                                    selected = isCustomContext
                                )
                            )
                        }

                        // Custom Context Input Field if active
                        if (isCustomContext) {
                            OutlinedTextField(
                                value = customContextText,
                                onValueChange = {
                                    customContextText = it
                                    val customVal = it.toIntOrNull()
                                    if (customVal != null && customVal > 0) {
                                        onConfigChange(engineConfig.copy(contextLength = customVal, customContextLength = customVal))
                                    }
                                },
                                label = { Text(I18n.t("custom_ctx_hint", language), fontSize = 10.sp) },
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
                        }
                    }
                }
            }

            // Inference Parameters (Temperature, Top P)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = I18n.t("temperature_label", language),
                                color = colors.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%.2f", engineConfig.temperature),
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Slider(
                            value = engineConfig.temperature,
                            onValueChange = { onConfigChange(engineConfig.copy(temperature = it)) },
                            valueRange = 0.0f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = I18n.t("top_p_label", language),
                                color = colors.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%.2f", engineConfig.topP),
                                color = colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Slider(
                            value = engineConfig.topP,
                            onValueChange = { onConfigChange(engineConfig.copy(topP = it)) },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                        )
                    }
                }
            }

            // System Role Toggle (Optional)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = I18n.t("sys_role_toggle", language),
                                color = colors.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = engineConfig.systemRoleEnabled,
                                onCheckedChange = { onConfigChange(engineConfig.copy(systemRoleEnabled = it)) }
                            )
                        }

                        if (engineConfig.systemRoleEnabled) {
                            Text(
                                text = I18n.t("all_presets", language),
                                color = colors.onSurface.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(GLOBAL_PRESETS) { preset ->
                                    val name = if (language == "tr") preset.nameTr else preset.nameEn
                                    val desc = if (language == "tr") preset.descriptionTr else preset.descriptionEn
                                    val isSelected = engineConfig.systemPrompt == preset.systemPrompt
                                    Box(
                                        modifier = Modifier
                                            .width(210.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.primaryContainer else colors.surface)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) colors.primary else colors.outline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onConfigChange(engineConfig.copy(systemPrompt = preset.systemPrompt)) }
                                            .padding(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = preset.icon, fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = name,
                                                    color = if (isSelected) colors.primary else colors.onSurface,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = desc,
                                                    color = colors.onSurface.copy(alpha = 0.6f),
                                                    fontSize = 9.sp,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = engineConfig.systemPrompt,
                                onValueChange = { onConfigChange(engineConfig.copy(systemPrompt = it)) },
                                label = { Text(I18n.t("sys_prompt_label", language), color = colors.onSurface.copy(0.6f), fontSize = 10.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
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
                        }
                    }
                }
            }

            // Advanced Model & Inference Parameters
            item {
                var isExpanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = I18n.t("advanced_settings_title", language),
                                        color = colors.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = I18n.t("advanced_settings_desc", language),
                                        color = colors.onSurface.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = colors.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        if (isExpanded) {
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))

                            // 1. Evaluation Batch Size (Değerlendirme parti boyutu)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(I18n.t("batch_size_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Text("${engineConfig.batchSize}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                                }
                                val batchOptions = listOf(128, 256, 512, 1024, 2048)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    batchOptions.forEach { opt ->
                                        val isSel = engineConfig.batchSize == opt
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primaryContainer else colors.surface)
                                                .border(1.dp, if (isSel) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                                .clickable { onConfigChange(engineConfig.copy(batchSize = opt)) }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$opt",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) colors.primary else colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Physical Batch Size (ubatch)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(I18n.t("physical_batch_size_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Text("${engineConfig.physicalBatchSize}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                                }
                                val ubatchOptions = listOf(64, 128, 256, 512)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ubatchOptions.forEach { opt ->
                                        val isSel = engineConfig.physicalBatchSize == opt
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primaryContainer else colors.surface)
                                                .border(1.dp, if (isSel) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                                .clickable { onConfigChange(engineConfig.copy(physicalBatchSize = opt)) }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$opt",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) colors.primary else colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Max Concurrent Predictions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(I18n.t("max_concurrent_predictions_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1, 2, 4, 8).forEach { cnt ->
                                        val isSel = engineConfig.maxConcurrentPredictions == cnt
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primaryContainer else colors.surface)
                                                .border(1.dp, if (isSel) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                                .clickable { onConfigChange(engineConfig.copy(maxConcurrentPredictions = cnt)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$cnt",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) colors.primary else colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Unified KV Cache
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(I18n.t("unified_kv_cache_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Switch(
                                    checked = engineConfig.unifiedKvCache,
                                    onCheckedChange = { onConfigChange(engineConfig.copy(unifiedKvCache = it)) }
                                )
                            }

                            // 5. Context Checkpoints / KV Shift
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(I18n.t("context_checkpoints_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Switch(
                                    checked = engineConfig.contextCheckpoints,
                                    onCheckedChange = { onConfigChange(engineConfig.copy(contextCheckpoints = it)) }
                                )
                            }

                            // 6. RoPE Frequency Base & Scale
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(I18n.t("rope_freq_base_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Text("${engineConfig.ropeFreqBase.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                                }
                                val ropeBases = listOf(10000.0f, 50000.0f, 100000.0f, 500000.0f, 1000000.0f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ropeBases.forEach { base ->
                                        val isSel = engineConfig.ropeFreqBase == base
                                        val displayStr = if (base >= 1000000f) "1M" else "${(base / 1000).toInt()}k"
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primaryContainer else colors.surface)
                                                .border(1.dp, if (isSel) colors.primary else colors.outline, RoundedCornerShape(6.dp))
                                                .clickable { onConfigChange(engineConfig.copy(ropeFreqBase = base)) }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayStr,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) colors.primary else colors.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // RoPE Frequency Scale Slider
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(I18n.t("rope_freq_scale_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Text(String.format("%.2f", engineConfig.ropeFreqScale), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = engineConfig.ropeFreqScale,
                                    onValueChange = { onConfigChange(engineConfig.copy(ropeFreqScale = it)) },
                                    valueRange = 0.5f..4.0f,
                                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                                )
                            }

                            // 7. Speculative Decoding
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(I18n.t("speculative_decoding_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                    Switch(
                                        checked = engineConfig.speculativeDecodingEnabled,
                                        onCheckedChange = { onConfigChange(engineConfig.copy(speculativeDecodingEnabled = it)) }
                                    )
                                }
                                if (engineConfig.speculativeDecodingEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(I18n.t("speculative_draft_tokens_label", language), fontSize = 10.sp, color = colors.onSurface.copy(alpha = 0.7f))
                                        Text("${engineConfig.speculativeDraftTokens}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, fontFamily = FontFamily.Monospace)
                                    }
                                    Slider(
                                        value = engineConfig.speculativeDraftTokens.toFloat(),
                                        onValueChange = { onConfigChange(engineConfig.copy(speculativeDraftTokens = it.toInt())) },
                                        valueRange = 1f..16f,
                                        steps = 14,
                                        colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                                    )
                                }
                            }

                            // 8. Flash Attention
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(I18n.t("flash_attention_label", language), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Switch(
                                    checked = engineConfig.flashAttention,
                                    onCheckedChange = { onConfigChange(engineConfig.copy(flashAttention = it)) }
                                )
                            }
                        }
                    }
                }
            }

            // Apply & Reload Model on Station Button
            item {
                Button(
                    onClick = onApplyAndReloadModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = I18n.t("apply_reload_model_btn", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Eject Model / Free GPU VRAM Button
            item {
                Button(
                    onClick = onUnloadModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(I18n.t("eject_vram_btn", language), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Action Start Chat Button (Blue)
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.ChatBubble, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = I18n.t("start_chat_btn", language),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = I18n.t("footer_aselens", language),
                        color = colors.onSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PresetItem(
    preset: ModelPreset,
    language: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val name = if (language == "tr") preset.nameTr else preset.nameEn
    val desc = if (language == "tr") preset.descriptionTr else preset.descriptionEn

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.primaryContainer else colors.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) colors.primary else colors.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = preset.icon, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = name,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
