package com.example.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nebula.ui.theme.AmoledBlack
import com.example.nebula.ui.theme.BorderBlack
import com.example.nebula.ui.theme.CreamBackground
import com.example.nebula.ui.theme.SunnyYellow

data class NavTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val locked: Boolean = false
)

val allAvailableTabs = listOf(
    NavTab("home", "Home", Icons.Filled.MusicNote),
    NavTab("search", "Search", Icons.Filled.Search),
    NavTab("playlists", "Playlists", Icons.Filled.PlaylistPlay),
    NavTab("library", "Library", Icons.Filled.LibraryMusic),
    NavTab("albums", "Albums", Icons.Filled.Album),
    NavTab("artists", "Artists", Icons.Filled.People),
    NavTab("folders", "Folders", Icons.Filled.Folder),
    NavTab("settings", "Settings", Icons.Filled.Settings, locked = true)
)

@Composable
fun TabCustomizerScreen(
    activeTabs: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    var visibleTabs by remember {
        mutableStateOf(activeTabs.mapNotNull { id -> allAvailableTabs.find { it.id == id } })
    }
    var hiddenTabs by remember {
        mutableStateOf(allAvailableTabs.filter { it.id !in activeTabs && !it.locked })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "CUSTOMIZE TABS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Reorder with arrows. Hide with down arrow below separator.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(visibleTabs) { index, tab ->
                TabCard(
                    tab = tab,
                    isFirst = index == 0,
                    isLast = index == visibleTabs.lastIndex,
                    onMoveUp = {
                        if (index > 0) {
                            visibleTabs = visibleTabs.toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                        }
                    },
                    onMoveDown = {
                        if (index < visibleTabs.lastIndex) {
                            visibleTabs = visibleTabs.toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                        }
                    },
                    onHide = {
                        if (!tab.locked && visibleTabs.size > 1) {
                            visibleTabs = visibleTabs.filter { it.id != tab.id }
                            hiddenTabs = hiddenTabs + tab
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).height(2.dp).background(BorderBlack))
                    Text(
                        "HIDDEN TABS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = BorderBlack,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(Modifier.weight(1f).height(2.dp).background(BorderBlack))
                }
            }

            itemsIndexed(hiddenTabs) { _, tab ->
                TabCard(
                    tab = tab,
                    isHidden = true,
                    onShow = {
                        if (visibleTabs.size < 5) {
                            hiddenTabs = hiddenTabs.filter { it.id != tab.id }
                            visibleTabs = visibleTabs + tab
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SunnyYellow)
                .border(3.dp, BorderBlack, RoundedCornerShape(16.dp))
                .clickable { onSave(visibleTabs.map { it.id }) },
            contentAlignment = Alignment.Center
        ) {
            Text("SAVE", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun TabCard(
    tab: NavTab,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isHidden: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onHide: () -> Unit = {},
    onShow: () -> Unit = {}
) {
    val bgColor = if (isHidden) MaterialTheme.colorScheme.background else CreamBackground
    val borderColor = if (tab.locked) SunnyYellow else BorderBlack

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 4.dp, top = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AmoledBlack)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(3.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (tab.locked) Icons.Filled.Lock else Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = if (isHidden) BorderBlack.copy(alpha = 0.4f) else BorderBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = if (isHidden) BorderBlack.copy(alpha = 0.4f) else BorderBlack,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    tab.label,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = if (isHidden) BorderBlack.copy(alpha = 0.4f) else BorderBlack
                )
            }

            if (!isHidden) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isFirst && !tab.locked) {
                        ArrowButton(Icons.Filled.ArrowUpward, onMoveUp)
                    }
                    if (!isLast && !tab.locked) {
                        ArrowButton(Icons.Filled.ArrowDownward, onMoveDown)
                    }
                    if (!tab.locked) {
                        ArrowButton(null, onHide, label = "✕")
                    }
                }
            } else {
                ArrowButton(null, onShow, label = "+")
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector?, onClick: () -> Unit, label: String? = null) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(com.example.nebula.ui.theme.NeonPink)
            .border(2.dp, BorderBlack, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
        } else {
            Text(label ?: "", fontWeight = FontWeight.Black, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
