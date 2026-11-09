package com.swift.browser

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- MATERIAL 3 EXPRESSIVE THEME IMPORTS ---
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

private val DBg = Color(0xFF101114)
private val DLine = Color(0xFF30343B)
private val DText = Color(0xFFF4F4F5)
private val DMuted = Color(0xFF9A9DA5)
private val DAccent = Color(0xFFFF6B35)
private val DRing = Color(0xFFB9C3FF)

private fun kindOf(d: DownloadItem): String {
    val m = d.mime ?: ""
    val n = d.filename.lowercase()
    return when {
        m.startsWith("video/") -> "Videos"
        m.startsWith("image/") -> "Images"
        m.startsWith("audio/") -> "Audio"
        m == "application/pdf" || m.startsWith("text/") || m.contains("officedocument") ||
            listOf(".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt").any { n.endsWith(it) } -> "Documents"
        else -> "Other"
    }
}

private fun iconOf(d: DownloadItem): ImageVector {
    val m = d.mime ?: ""
    return when {
        m.startsWith("video/") -> Icons.Rounded.Movie
        m.startsWith("image/") -> Icons.Rounded.Image
        m.startsWith("audio/") -> Icons.Rounded.MusicNote
        m == "application/vnd.android.package-archive" -> Icons.Rounded.Android
        kindOf(d) == "Documents" -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}

private fun dayLabel(ts: Long): String {
    val d = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ts))
    fun key(t: Long) = java.text.SimpleDateFormat("yyyyDDD", java.util.Locale.US).format(java.util.Date(t))
    val now = System.currentTimeMillis()
    return when (key(ts)) {
        key(now) -> "Today - $d"
        key(now - 86_400_000L) -> "Yesterday - $d"
        else -> d
    }
}

/**
 * Brave-style downloads list. Tap a running download to pause it, tap it again to
 * resume; tap a finished one to open it. The 3-dot menu has the extras only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val all = DownloadEngine.items // snapshot-state list: recomposes on every progress update
    var filter by remember { mutableStateOf("All") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val totalSpace = remember {
        runCatching {
            android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).totalBytes
        }.getOrDefault(0L)
    }
    val fmt = { n: Long -> android.text.format.Formatter.formatFileSize(context, n) }

    val shown = all.filter {
        (filter == "All" || kindOf(it) == filter) && (query.isBlank() || it.filename.contains(query, true))
    }
    val active = shown.filter { it.state != DlState.DONE }.sortedByDescending { it.created }
    val finished = shown.filter { it.state == DlState.DONE }.sortedByDescending { it.finished }
    val byDay = LinkedHashMap<String, MutableList<DownloadItem>>()
    finished.forEach { byDay.getOrPut(dayLabel(it.finished)) { mutableListOf() }.add(it) }
    val used = all.filter { it.state == DlState.DONE }.sumOf { maxOf(it.total, it.done) }

    Column(Modifier.fillMaxSize().background(DBg)) {
        // Expressive Top App Bar with Search toggle
        AnimatedContent(
            targetState = searching,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "SearchBarAnimation"
        ) { isSearching ->
            if (isSearching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = DText)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = DText, fontSize = 18.sp),
                        cursorBrush = SolidColor(DAccent),
                        modifier = Modifier.weight(1f).focusRequester(focus).padding(horizontal = 8.dp),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) Text("Search downloads", color = DMuted, fontSize = 18.sp)
                            innerTextField()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, "Clear", tint = DMuted)
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("Downloads", fontWeight = FontWeight.SemiBold, color = DText) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, "Close", tint = DText)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, "Download settings", tint = DText)
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, "Search downloads", tint = DText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DBg)
                )
            }
        }

        Text(
            "Using ${fmt(used)}" + (if (totalSpace > 0) " of ${fmt(totalSpace)}" else ""),
            color = DMuted, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 4.dp)
        )
        HorizontalDivider(color = DLine, modifier = Modifier.padding(top = 8.dp))

        // Expressive Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "All" to null, "Videos" to Icons.Rounded.Movie, "Images" to Icons.Rounded.Image,
                "Audio" to Icons.Rounded.MusicNote, "Documents" to Icons.Rounded.Description,
                "Other" to Icons.Rounded.InsertDriveFile
            )
            items(filters) { (name, icon) ->
                val isSelected = filter == name
                FilterChip(
                    selected = isSelected,
                    onClick = { filter = name },
                    label = { Text(name, fontSize = 14.sp, color = if (isSelected) Color.White else DText) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else if (icon != null) {
                        { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DAccent,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color.Transparent,
                        labelColor = DText,
                        iconColor = DText
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = DLine,
                        selectedBorderColor = Color.Transparent
                    )
                )
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = DMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (active.isNotEmpty()) {
                    item(key = "h-now") { SectionTitle("Just now") }
                    items(active, key = { it.id }) { d -> DownloadRowItem(d, fmt) }
                }
                byDay.forEach { (label, list) ->
                    item(key = "h-$label") { SectionTitle(label) }
                    items(list, key = { it.id }) { d -> DownloadRowItem(d, fmt) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = DText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 17.dp, end = 17.dp, top = 20.dp, bottom = 6.dp)
    )
}

private fun etaText(d: DownloadItem): String? {
    if (d.state != DlState.RUNNING || d.total <= 0 || d.speed <= 0) return null
    val secs = ((d.total - d.done) / d.speed).coerceAtLeast(1)
    return when {
        secs < 60 -> "$secs secs left"
        secs < 3600 -> "${secs / 60} mins left"
        else -> "${secs / 3600} hrs left"
    }
}

@Composable
private fun DownloadRowItem(d: DownloadItem, fmt: (Long) -> String) {
    val context = LocalContext.current
    val done = d.state == DlState.DONE
    val failed = d.state == DlState.FAILED
    val running = d.state == DlState.RUNNING
    val frac = if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0f
    val host = runCatching { android.net.Uri.parse(d.url).host }.getOrNull().orEmpty().removePrefix("www.")
    var menu by remember { mutableStateOf(false) }

    val subtitle = when {
        done -> fmt(maxOf(d.total, d.done)) + (if (host.isNotEmpty()) " • $host" else "")
        failed -> "Failed • tap to retry" + (if (d.done > 0) " • ${fmt(d.done)}" else "")
        d.state == DlState.PAUSED -> "Paused • " + (if (d.total > 0) "${fmt(d.done)}/${fmt(d.total)}" else fmt(d.done))
        d.state == DlState.QUEUED -> "Waiting…"
        d.total > 0 -> "${fmt(d.done)}/${fmt(d.total)}" + (etaText(d)?.let { " • $it" } ?: "")
        else -> fmt(d.done)
    }

    ListItem(
        headlineContent = { 
            Text(d.filename, color = DText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
        },
        supportingContent = { 
            Text(subtitle, color = DMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
        },
        leadingContent = {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (!done && !failed) {
                    if (d.total > 0) {
                        CircularProgressIndicator(
                            progress = { frac }, modifier = Modifier.fillMaxSize(),
                            color = DRing, trackColor = DLine, strokeWidth = 3.dp
                        )
                    } else if (running) {
                        CircularProgressIndicator(modifier = Modifier.fillMaxSize(), color = DRing, strokeWidth = 3.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { 0f }, modifier = Modifier.fillMaxSize(),
                            color = DRing, trackColor = DLine, strokeWidth = 3.dp
                        )
                    }
                    Icon(
                        when (d.state) {
                            DlState.RUNNING -> Icons.Rounded.Pause
                            DlState.PAUSED -> Icons.Rounded.PlayArrow
                            else -> Icons.Rounded.Download
                        },
                        null, tint = DText, modifier = Modifier.size(22.dp)
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF1B1D21)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (failed) Icons.Rounded.Refresh else iconOf(d), null,
                            tint = if (failed) Color(0xFFE5484D) else DText
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (!done) {
                IconButton(onClick = { DownloadEngine.cancel(d.id) }) {
                    Icon(Icons.Rounded.Close, "Cancel download", tint = DText)
                }
            } else {
                Box {
                    IconButton(onClick = { menu = true }) { 
                        Icon(Icons.Rounded.MoreVert, "More", tint = DText) 
                    }
                    DropdownMenu(
                        expanded = menu, 
                        onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xFF23262B)
                    ) {
                        MenuItem("Share", Icons.Rounded.Share) { menu = false; DownloadActions.share(context, d) }
                        if (d.url.startsWith("http")) {
                            MenuItem("Copy link", Icons.Rounded.ContentCopy) {
                                menu = false
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Link", d.url))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                        MenuItem("Remove from list", Icons.Rounded.PlaylistRemove) {
                            menu = false
                            DownloadEngine.remove(d.id, deleteFile = false)
                        }
                        MenuItem("Delete file", Icons.Rounded.Delete) {
                            menu = false
                            DownloadEngine.remove(d.id, deleteFile = true)
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (done) DownloadActions.open(context, d) else DownloadEngine.toggle(d.id)
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = DText) },
        leadingIcon = { Icon(icon, null, tint = DMuted) },
        onClick = onClick
    )
}