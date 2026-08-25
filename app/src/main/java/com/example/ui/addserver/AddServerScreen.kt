package com.example.ui.addserver

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppResult
import com.example.core.SecretRedactor
import com.example.data.model.ServerTestStatus
import com.example.data.model.VlessProfile
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.ServerViewModel
import com.example.vless.VlessParser
import com.example.vless.VlessValidator
import com.example.vpn.ServerTester
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    serverViewModel: ServerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }

    // --- Simple Mode States ---
    var simpleUrl by remember { mutableStateOf("") }
    var parsedPreview by remember { mutableStateOf<VlessProfile?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // --- Advanced Mode States ---
    var advName by remember { mutableStateOf("Custom VLESS") }
    var advAddress by remember { mutableStateOf("") }
    var advPort by remember { mutableStateOf("443") }
    var advUuid by remember { mutableStateOf("") }
    var advTransport by remember { mutableStateOf("tcp") }
    var advSecurity by remember { mutableStateOf("reality") }
    var advSni by remember { mutableStateOf("") }
    var advHost by remember { mutableStateOf("") }
    var advPath by remember { mutableStateOf("/") }
    var advServiceName by remember { mutableStateOf("") }
    var advFlow by remember { mutableStateOf("xtls-rprx-vision") }
    var advFingerprint by remember { mutableStateOf("chrome") }
    var advPublicKey by remember { mutableStateOf("") }
    var advShortId by remember { mutableStateOf("") }
    var advSpiderX by remember { mutableStateOf("/") }
    var advAlpn by remember { mutableStateOf("") }

    // Dropdown states
    var transportExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }

    // Connectivity test state
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<ServerTestStatus?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add VLESS Server",
                color = AppTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mode Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = AppTheme.colors.surfaceCard,
            contentColor = AppTheme.colors.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = AppTheme.colors.primary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        "Quick Import / URL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selectedTabIndex == 0) AppTheme.colors.primary else AppTheme.colors.textSecondary
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Text(
                        "Advanced Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selectedTabIndex == 1) AppTheme.colors.primary else AppTheme.colors.textSecondary
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tab Content
        if (selectedTabIndex == 0) {
            // --- SIMPLE MODE ---
            Text(
                text = "PASTE VLESS LINK",
                color = AppTheme.colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = simpleUrl,
                onValueChange = { input ->
                    simpleUrl = input
                    testResultStatus = null
                    if (input.isNotBlank()) {
                        when (val res = VlessParser.parse(input)) {
                            is AppResult.Success -> {
                                parsedPreview = res.data
                                parseError = null
                            }
                            is AppResult.Error -> {
                                parsedPreview = null
                                parseError = res.userFriendlyMessage
                            }
                        }
                    } else {
                        parsedPreview = null
                        parseError = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vless_url_input"),
                placeholder = { Text("vless://uuid@host:port?type=...#ServerName", color = AppTheme.colors.textMuted, fontSize = 12.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.colors.surfaceCard,
                    unfocusedContainerColor = AppTheme.colors.surfaceCard,
                    focusedBorderColor = AppTheme.colors.primary,
                    unfocusedBorderColor = AppTheme.colors.borderSubtle,
                    focusedTextColor = AppTheme.colors.textPrimary,
                    unfocusedTextColor = AppTheme.colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Paste & QR/Batch Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clip = clipboardManager.getText()?.text ?: ""
                        if (clip.isNotBlank()) {
                            simpleUrl = clip
                            when (val res = VlessParser.parse(clip)) {
                                is AppResult.Success -> {
                                    parsedPreview = res.data
                                    parseError = null
                                }
                                is AppResult.Error -> {
                                    parsedPreview = null
                                    parseError = res.userFriendlyMessage
                                }
                            }
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste", color = AppTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AppTheme.colors.metricDownload, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR / Batch", color = AppTheme.colors.metricDownload, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Card
            if (parseError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (AppTheme.colors.isDark) Color(0xFF241416) else Color(0xFFFEF2F2)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.statusError.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AppTheme.colors.statusError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(parseError ?: "", color = AppTheme.colors.statusError, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Parsed Config Preview Card
            if (parsedPreview != null) {
                val p = parsedPreview!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.statusConnected.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AppTheme.colors.statusConnected, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Valid VLESS Configuration", color = AppTheme.colors.statusConnected, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        PreviewRow("Server Name", p.name)
                        PreviewRow("Address:Port", "${p.address}:${p.port}")
                        PreviewRow("Transport", p.transport.uppercase())
                        PreviewRow("Security", p.securityBadge)
                        PreviewRow("UUID", SecretRedactor.maskUuid(p.uuid))
                        if (p.sni.isNotBlank()) PreviewRow("SNI", p.sni)
                        if (p.publicKey.isNotBlank()) PreviewRow("Public Key", "${p.publicKey.take(8)}...")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

        } else {
            // --- ADVANCED MODE ---
            Text("SERVER BASICS", color = AppTheme.colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCustomField("Server Name", advName) { advName = it }
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(2.5f)) {
                    OutlinedCustomField("Host / Address", advAddress) { advAddress = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedCustomField("Port", advPort) { advPort = it }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCustomField("UUID (User ID)", advUuid) { advUuid = it }
            Spacer(modifier = Modifier.height(14.dp))

            Text("PROTOCOL & SECURITY", color = AppTheme.colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Transport Selector
            ExposedDropdownMenuBox(
                expanded = transportExpanded,
                onExpandedChange = { transportExpanded = !transportExpanded }
            ) {
                OutlinedTextField(
                    value = advTransport.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Transport", color = AppTheme.colors.textSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transportExpanded) },
                    colors = customTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = transportExpanded,
                    onDismissRequest = { transportExpanded = false },
                    modifier = Modifier
                        .background(AppTheme.colors.surfaceCard)
                        .border(1.dp, AppTheme.colors.borderSubtle)
                ) {
                    listOf("tcp", "ws", "grpc", "http", "h2", "quic").forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.uppercase(), color = AppTheme.colors.textPrimary) },
                            onClick = {
                                advTransport = item
                                transportExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Security Selector
            ExposedDropdownMenuBox(
                expanded = securityExpanded,
                onExpandedChange = { securityExpanded = !securityExpanded }
            ) {
                OutlinedTextField(
                    value = advSecurity.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Security Layer", color = AppTheme.colors.textSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityExpanded) },
                    colors = customTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = securityExpanded,
                    onDismissRequest = { securityExpanded = false },
                    modifier = Modifier
                        .background(AppTheme.colors.surfaceCard)
                        .border(1.dp, AppTheme.colors.borderSubtle)
                ) {
                    listOf("reality", "tls", "none").forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.uppercase(), color = AppTheme.colors.textPrimary) },
                            onClick = {
                                advSecurity = item
                                securityExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCustomField("SNI / Server Name", advSni) { advSni = it }
            Spacer(modifier = Modifier.height(8.dp))

            if (advSecurity == "reality") {
                OutlinedCustomField("Public Key (pbk)", advPublicKey) { advPublicKey = it }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCustomField("Short ID (sid)", advShortId) { advShortId = it }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCustomField("SpiderX (spx)", advSpiderX) { advSpiderX = it }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (advTransport == "ws" || advTransport == "http") {
                OutlinedCustomField("Host Header", advHost) { advHost = it }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedCustomField("Path", advPath) { advPath = it }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (advTransport == "grpc") {
                OutlinedCustomField("Service Name", advServiceName) { advServiceName = it }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedCustomField("Flow (e.g. xtls-rprx-vision)", advFlow) { advFlow = it }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCustomField("Fingerprint (e.g. chrome, firefox)", advFingerprint) { advFingerprint = it }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Test Result Indicator
        if (testResultStatus != null) {
            val (statusText, statusColor) = when (val res = testResultStatus!!) {
                is ServerTestStatus.Available -> Pair("Available • ${res.latencyMs}ms roundtrip latency", AppTheme.colors.statusConnected)
                is ServerTestStatus.Slow -> Pair("Slow • ${res.latencyMs}ms roundtrip latency", AppTheme.colors.statusWarning)
                is ServerTestStatus.Unavailable -> Pair("Unavailable: ${res.reason}", AppTheme.colors.statusError)
                is ServerTestStatus.InvalidConfig -> Pair("Config Error: ${res.error}", AppTheme.colors.statusError)
                else -> Pair("Testing...", AppTheme.colors.primary)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkPing, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Bottom Action Buttons: Test Connection & Save Server
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Test Connection Button
            Button(
                onClick = {
                    val profileToTest = if (selectedTabIndex == 0) {
                        parsedPreview
                    } else {
                        buildProfileFromAdvanced(
                            advName, advAddress, advPort, advUuid, advTransport,
                            advSecurity, advSni, advHost, advPath, advServiceName,
                            advFlow, advFingerprint, advPublicKey, advShortId, advSpiderX, advAlpn
                        )
                    }

                    if (profileToTest != null) {
                        isTestingConnection = true
                        scope.launch {
                            val testRes = ServerTester.testServer(profileToTest)
                            testResultStatus = testRes.status
                            isTestingConnection = false
                        }
                    } else {
                        Toast.makeText(context, "Please enter valid server parameters first", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isTestingConnection,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppTheme.colors.primary)
                } else {
                    Icon(Icons.Default.NetworkPing, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Ping", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Save Server Button
            Button(
                onClick = {
                    val profileToSave = if (selectedTabIndex == 0) {
                        parsedPreview
                    } else {
                        buildProfileFromAdvanced(
                            advName, advAddress, advPort, advUuid, advTransport,
                            advSecurity, advSni, advHost, advPath, advServiceName,
                            advFlow, advFingerprint, advPublicKey, advShortId, advSpiderX, advAlpn
                        )
                    }

                    if (profileToSave != null) {
                        val addResult = serverViewModel.addServer(profileToSave)
                        if (addResult.isSuccess) {
                            Toast.makeText(context, "Server '${profileToSave.name}' saved!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            val err = addResult as AppResult.Error
                            Toast.makeText(context, err.userFriendlyMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Please fill required fields (Address, Port, UUID)", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f).testTag("save_server_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AppTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Server", color = AppTheme.colors.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showImportDialog) {
        QrImportDialog(
            onDismiss = { showImportDialog = false },
            onImportSuccess = { parsedProfile ->
                showImportDialog = false
                serverViewModel.addServer(parsedProfile)
                Toast.makeText(context, "Imported '${parsedProfile.name}'", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            },
            onBatchImport = { rawBatchText ->
                showImportDialog = false
                val batchRes = serverViewModel.importBatch(rawBatchText)
                Toast.makeText(
                    context,
                    "Imported ${batchRes.successfulProfiles.size} servers (${batchRes.failedEntries.size} failed)",
                    Toast.LENGTH_LONG
                ).show()
                onNavigateBack()
            }
        )
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
        Text(value, color = AppTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OutlinedCustomField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp) },
        singleLine = true,
        colors = customTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppTheme.colors.surfaceCard,
    unfocusedContainerColor = AppTheme.colors.surfaceCard,
    focusedBorderColor = AppTheme.colors.primary,
    unfocusedBorderColor = AppTheme.colors.borderSubtle,
    focusedTextColor = AppTheme.colors.textPrimary,
    unfocusedTextColor = AppTheme.colors.textPrimary
)

private fun buildProfileFromAdvanced(
    name: String,
    address: String,
    portStr: String,
    uuid: String,
    transport: String,
    security: String,
    sni: String,
    host: String,
    path: String,
    serviceName: String,
    flow: String,
    fingerprint: String,
    publicKey: String,
    shortId: String,
    spiderX: String,
    alpn: String
): VlessProfile? {
    val port = portStr.toIntOrNull() ?: return null
    if (address.isBlank() || uuid.isBlank()) return null

    val profile = VlessProfile(
        name = if (name.isNotBlank()) name else "VLESS Server",
        address = address.trim(),
        port = port,
        uuid = uuid.trim(),
        encryption = "none",
        transport = transport.lowercase(),
        security = security.lowercase(),
        sni = sni.trim(),
        host = host.trim(),
        path = if (path.isNotBlank()) path.trim() else "/",
        serviceName = serviceName.trim(),
        flow = flow.trim(),
        fingerprint = fingerprint.trim(),
        publicKey = publicKey.trim(),
        shortId = shortId.trim(),
        spiderX = spiderX.trim(),
        alpn = alpn.trim()
    )

    return try {
        VlessValidator.validate(profile)
        profile
    } catch (_: Exception) {
        null
    }
}
