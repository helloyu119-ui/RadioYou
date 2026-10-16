package com.example.radioyou

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadioYouTheme {
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                RadioScreen(vm)
            }
        }
    }
}

private val countries = listOf("JP" to "日本", "US" to "US", "GB" to "UK", "DE" to "DE", "FR" to "FR", "KR" to "KR", "" to "全世界")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(vm: RadioViewModel) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val list = vm.visibleStations()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("RadioYou") },
                actions = {
                    IconButton(onClick = { vm.favoritesOnly = !vm.favoritesOnly }) {
                        Icon(
                            if (vm.favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "お気に入りのみ表示"
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            AnimatedVisibility(vm.current != null) {
                vm.current?.let { NowPlayingBar(vm, it) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchBar(vm)
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(countries) { (code, label) ->
                        FilterChip(
                            selected = vm.country == code,
                            onClick = { vm.onCountryChange(code) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (list.isEmpty() && !vm.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (vm.favoritesOnly) "お気に入りはまだありません" else "局が見つかりません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.uuid }) { s ->
                    StationRow(
                        s,
                        selected = vm.current?.uuid == s.uuid,
                        fav = s.uuid in vm.favorites,
                        onPlay = { vm.play(s) },
                        onFav = { vm.toggleFavorite(s) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(vm: RadioViewModel) {
    OutlinedTextField(
        value = vm.query,
        onValueChange = vm::onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("局名で検索") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) {
                Icon(Icons.Filled.Clear, "クリア")
            }
        },
        singleLine = true,
        shape = CircleShape,
    )
}

@Composable
private fun StationRow(s: Station, selected: Boolean, fav: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onPlay),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
        headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val sub = listOf(s.country, s.tags.split(",").take(2).joinToString(", "), if (s.bitrate > 0) "${s.bitrate}kbps" else "")
                .filter { it.isNotBlank() }.joinToString(" ・ ")
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(onClick = onFav) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "お気に入り",
                    tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
private fun NowPlayingBar(vm: RadioViewModel, s: Station) {
    Surface(
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (vm.buffering) "読み込み中…" else if (vm.isPlaying) "再生中" else "一時停止",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledIconButton(onClick = vm::togglePlay) {
                if (vm.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "再生/一時停止")
            }
            IconButton(onClick = vm::stop) { Icon(Icons.Filled.Close, "停止") }
        }
    }
}
