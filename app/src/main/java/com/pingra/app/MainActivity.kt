package com.pingra.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pingra.app.data.SmsDirection
import com.pingra.app.data.SmsMessage
import com.pingra.app.data.SmsRepository
import com.pingra.app.data.SmsThread
import com.pingra.app.data.UNKNOWN_SENDER
import com.pingra.app.sms.SmsRoleHelper
import com.pingra.app.ui.theme.PingraTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val launchRecipient = mutableStateOf<String?>(null)
    private val launchBody = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureLaunchIntent(intent)

        setContent {
            PingraTheme {
                PingraApp(
                    repository = remember { SmsRepository(this) },
                    initialRecipient = launchRecipient.value,
                    initialBody = launchBody.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureLaunchIntent(intent)
    }

    private fun captureLaunchIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme !in smsSchemes) return

        launchRecipient.value = data.schemeSpecificPart
            ?.substringBefore("?")
            ?.let(Uri::decode)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        launchBody.value = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: ""
    }

    private companion object {
        val smsSchemes = setOf("sms", "smsto", "mms", "mmsto")
    }
}

private val requiredSmsPermissions = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_MMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.RECEIVE_WAP_PUSH,
    Manifest.permission.SEND_SMS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PingraApp(
    repository: SmsRepository,
    initialRecipient: String?,
    initialBody: String,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val roleHelper = remember(context) { SmsRoleHelper(context) }

    var roleAvailable by remember { mutableStateOf(roleHelper.isSmsRoleAvailable()) }
    var isDefaultSmsApp by remember { mutableStateOf(roleHelper.isDefaultSmsApp()) }
    var missingPermissions by remember { mutableStateOf(context.missingSmsPermissions()) }
    var accessRefreshKey by remember { mutableIntStateOf(0) }

    fun refreshAccessState() {
        roleAvailable = roleHelper.isSmsRoleAvailable()
        isDefaultSmsApp = roleHelper.isDefaultSmsApp()
        missingPermissions = context.missingSmsPermissions()
        accessRefreshKey += 1
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshAccessState()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshAccessState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAccessState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val telephonyCapable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pingra",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            when {
                !telephonyCapable -> AccessState(
                    title = "SMS is unavailable",
                    body = "This device does not report SMS messaging support.",
                    actionLabel = null,
                    onAction = null,
                )

                !roleAvailable -> AccessState(
                    title = "SMS role unavailable",
                    body = "This Android build does not expose the default SMS app role.",
                    actionLabel = null,
                    onAction = null,
                )

                !isDefaultSmsApp -> AccessState(
                    title = "Make Pingra default",
                    body = "Android requires the default SMS role before Pingra can read and manage texts.",
                    actionLabel = "Set default",
                    onAction = {
                        roleHelper.createRequestRoleIntent()?.let(roleLauncher::launch)
                    },
                )

                missingPermissions.isNotEmpty() -> AccessState(
                    title = "Allow SMS access",
                    body = "Pingra needs SMS permissions for sending and receiving texts.",
                    actionLabel = "Allow",
                    onAction = {
                        permissionLauncher.launch(requiredSmsPermissions)
                    },
                )

                else -> SmsHome(
                    repository = repository,
                    initialRecipient = initialRecipient,
                    initialBody = initialBody,
                    accessRefreshKey = accessRefreshKey,
                )
            }
        }
    }
}

@Composable
private fun AccessState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SmsHome(
    repository: SmsRepository,
    initialRecipient: String?,
    initialBody: String,
    accessRefreshKey: Int,
) {
    val scope = rememberCoroutineScope()
    var threads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var selectedThread by remember { mutableStateOf<SmsThread?>(null) }
    var loadingThreads by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var threadRefreshKey by remember { mutableIntStateOf(0) }

    fun refreshThreads() {
        scope.launch {
            loadingThreads = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadThreads()
                }
            }.onSuccess { loadedThreads ->
                threads = loadedThreads
                selectedThread = selectedThread?.let { current ->
                    if (current.threadId >= 0) {
                        loadedThreads.firstOrNull { it.threadId == current.threadId } ?: current
                    } else {
                        loadedThreads.firstOrNull { it.address == current.address } ?: current
                    }
                }
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Could not load messages."
            }
            loadingThreads = false
        }
    }

    LaunchedEffect(accessRefreshKey, threadRefreshKey) {
        refreshThreads()
    }

    LaunchedEffect(initialRecipient, threads) {
        val recipient = initialRecipient?.trim().orEmpty()
        if (recipient.isNotBlank()) {
            selectedThread = threads.firstOrNull { it.address == recipient }
                ?: SmsThread(
                    threadId = -1,
                    address = recipient,
                    snippet = "",
                    timestampMillis = System.currentTimeMillis(),
                    messageCount = 0,
                )
        }
    }

    selectedThread?.let { thread ->
        ConversationPane(
            thread = thread,
            repository = repository,
            initialBody = initialBody,
            onBack = { selectedThread = null },
            onSent = { address ->
                selectedThread = selectedThread?.copy(address = address)
                threadRefreshKey += 1
            },
        )
    } ?: ThreadListPane(
        threads = threads,
        loading = loadingThreads,
        errorMessage = errorMessage,
        onRefresh = ::refreshThreads,
        onNew = {
            selectedThread = SmsThread(
                threadId = -1,
                address = "",
                snippet = "",
                timestampMillis = System.currentTimeMillis(),
                messageCount = 0,
            )
        },
        onSelect = { selectedThread = it },
    )
}

@Composable
private fun ThreadListPane(
    threads: List<SmsThread>,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onSelect: (SmsThread) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onNew) {
                Text("New")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (!loading && threads.isEmpty()) {
            EmptyState(text = "No messages yet.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(threads, key = { it.threadId }) { thread ->
                    ThreadRow(
                        thread = thread,
                        onClick = { onSelect(thread) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: SmsThread,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.address,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = thread.snippet.ifBlank { "No message text" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatTimestamp(thread.timestampMillis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationPane(
    thread: SmsThread,
    repository: SmsRepository,
    initialBody: String,
    onBack: () -> Unit,
    onSent: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember(thread.threadId) { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var loading by remember(thread.threadId) { mutableStateOf(false) }
    var recipient by remember(thread.address) { mutableStateOf(thread.address) }
    var draft by remember(thread.address) { mutableStateOf(initialBody) }
    var errorMessage by remember(thread.threadId) { mutableStateOf<String?>(null) }
    var refreshKey by remember(thread.threadId) { mutableIntStateOf(0) }

    fun refreshMessages() {
        scope.launch {
            loading = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadMessages(thread.threadId)
                }
            }.onSuccess { loadedMessages ->
                messages = loadedMessages
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Could not load this conversation."
            }
            loading = false
        }
    }

    LaunchedEffect(thread.threadId, refreshKey) {
        refreshMessages()
    }

    LaunchedEffect(thread.address, initialBody) {
        if (initialBody.isNotBlank()) draft = initialBody
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            Text(
                text = recipient.ifBlank { "New message" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = ::refreshMessages) {
                Text("Refresh")
            }
        }

        if (thread.threadId < 0) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                label = {
                    Text("To")
                },
            )
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (!loading && messages.isEmpty()) {
            EmptyState(
                text = if (thread.threadId < 0) {
                    "New conversation."
                } else {
                    "No visible messages."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }
        }

        MessageComposer(
            draft = draft,
            canSend = recipient.isNotBlank() && recipient != UNKNOWN_SENDER && draft.isNotBlank(),
            onDraftChange = { draft = it },
            onSend = {
                val address = recipient.trim()
                val body = draft
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repository.sendMessage(address, body)
                        }
                    }.onSuccess {
                        draft = ""
                        Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                        refreshKey += 1
                        onSent(address)
                    }.onFailure { throwable ->
                        Toast.makeText(
                            context,
                            throwable.message ?: "Could not send message",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    val outgoing = message.direction == SmsDirection.Outgoing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (outgoing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (outgoing) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.body.ifBlank { " " },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MessageComposer(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            minLines = 1,
            maxLines = 4,
            placeholder = {
                Text("Message")
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = canSend,
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.missingSmsPermissions(): List<String> {
    return requiredSmsPermissions.filter { permission ->
        ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestampMillis))
}
