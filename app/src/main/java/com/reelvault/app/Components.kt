package com.reelvault.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.io.File

@Composable
fun Thumb(uri: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val req = remember(uri) {
        ImageRequest.Builder(ctx).data(uri).videoFrameMillis(500).size(360, 640)
            .memoryCacheKey(uri).diskCacheKey(uri).build()
    }
    AsyncImage(model = req, contentDescription = null, modifier = modifier, contentScale = scale)
}

@Composable
fun Avatar(path: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).border(2.dp, AccentBrush, CircleShape).padding(3.dp).clip(CircleShape)
            .background(AccentBrush),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNotBlank() && File(path).isFile)
            AsyncImage(File(path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (name.isNotBlank())
            Text(name.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        else Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun VideoCard(v: VideoEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.bounceClick(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF241A45), Color(0xFF10172F))))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
        ) {
            Thumb(v.uri, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
            Text(fmtDur(v.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (v.favorite) Icon(Icons.Rounded.Favorite, null, tint = Pink,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))
        }
        Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

@Composable
fun VideoRow(v: VideoEntity, onClick: () -> Unit, onFav: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).glass(shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(60.dp).aspectRatio(9f / 14f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF241A45))) {
            Thumb(v.uri, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text("${fmtDur(v.durationMs)}  •  ${fmtSize(v.size)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            Text(v.folder, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        GlassIconButton(if (v.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", onFav,
            size = 40.dp, tint = if (v.favorite) Pink else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun VideoGrid(list: List<VideoEntity>, state: LazyGridState, bottomPad: Dp, onClick: (Int) -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(3), state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { itemsIndexed(list, key = { _, v -> v.uri }) { i, v -> VideoCard(v) { onClick(i) } } }
}

@Composable
fun VideoList(list: List<VideoEntity>, state: LazyListState, bottomPad: Dp, onClick: (Int) -> Unit, onFav: (String) -> Unit) {
    LazyColumn(
        state = state, verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
    ) { itemsIndexed(list, key = { _, v -> v.uri }) { i, v -> VideoRow(v, { onClick(i) }, { onFav(v.uri) }) } }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(84.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Violet, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        if (action != null) { Spacer(Modifier.height(6.dp)); action() }
    }
}

@Composable
fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(top = 8.dp)
)
