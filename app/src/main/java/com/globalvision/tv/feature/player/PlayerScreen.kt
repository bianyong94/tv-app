package com.globalvision.tv.feature.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.globalvision.tv.core.model.TvEpisode
import com.globalvision.tv.core.model.TvPlaySource
import com.globalvision.tv.core.network.TvRepository
import com.globalvision.tv.core.player.TvPlayerController
import com.globalvision.tv.ui.theme.TvFocusBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SOURCE_LOAD_TIMEOUT_MS = 20_000L

@Composable
fun PlayerScreen(
    repository: TvRepository,
    title: String,
    movieId: String,
    sourceIndex: Int,
    episodeIndex: Int,
    onBack: () -> Unit,
) {
    val tag = "PlayerScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { TvPlayerController(context) }

    var showControls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryInProgress by remember { mutableStateOf(false) }
    var waitingForPlayableSource by remember { mutableStateOf(false) }
    var initialPlaybackTriggered by rememberSaveable(movieId) { mutableStateOf(false) }
    var playAttemptToken by remember { mutableIntStateOf(0) }
    var sources by remember(movieId) { mutableStateOf<List<TvPlaySource>>(emptyList()) }
    val episodeCache = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var currentSourceIndex by rememberSaveable(movieId) {
        mutableIntStateOf(sourceIndex.coerceAtLeast(0))
    }
    var currentEpisodeIndex by rememberSaveable(movieId) {
        mutableIntStateOf(episodeIndex.coerceAtLeast(0))
    }

    val playPauseFocusRequester = remember { FocusRequester() }

    fun flashControls() {
        showControls = true
        controlsTick += 1
    }

    fun beginPlayback(playUrl: String, loadingText: String = "正在加载视频...") {
        errorMessage = null
        loadingMessage = loadingText
        waitingForPlayableSource = true
        playAttemptToken += 1
        controller.play(playUrl)
        flashControls()
    }

    LaunchedEffect(movieId) {
        Log.d(
            tag,
            "enter player: title=$title movieId=$movieId sourceIndex=$sourceIndex episodeIndex=$episodeIndex",
        )
        if (movieId.isBlank()) {
            errorMessage = "当前没有可播放地址，请返回后重新选择。"
            return@LaunchedEffect
        }
        initialPlaybackTriggered = false
        loadingMessage = "正在准备可播放来源..."
        val detail = try {
            repository.getDetail(movieId)
        } catch (throwable: Throwable) {
            Log.w(tag, "load detail for player failed: movieId=$movieId", throwable)
            null
        }
        sources = detail?.sources.orEmpty()
        if (sources.isNotEmpty()) {
            currentSourceIndex = sourceIndex.coerceIn(0, sources.lastIndex)
            currentEpisodeIndex = episodeIndex.coerceAtLeast(0)
        } else {
            errorMessage = "当前视频暂无可播放来源，请返回重新选择。"
        }
    }

    suspend fun loadEpisodesForSource(source: TvPlaySource): List<TvEpisode> {
        episodeCache[source.code]?.let { return it }
        val fetched = try {
            repository.getEpisodes(movieId, source.code)
        } catch (throwable: Throwable) {
            Log.w(tag, "load episodes for player failed: movieId=$movieId source=${source.code}", throwable)
            source.episodes
        }
        episodeCache[source.code] = fetched
        return fetched
    }

    suspend fun playSourceEpisode(targetSourceIndex: Int, targetEpisodeIndex: Int): Boolean {
        val source = sources.getOrNull(targetSourceIndex) ?: return false
        val episodes = loadEpisodesForSource(source)
        if (episodes.isEmpty()) return false
        val safeEpisode = targetEpisodeIndex.coerceIn(0, episodes.lastIndex)
        val episode = episodes.getOrNull(safeEpisode) ?: return false
        val playUrl = try {
            repository.resolveEpisodeUrl(episode)
        } catch (throwable: Throwable) {
            Log.w(tag, "resolve episode failed: movieId=$movieId source=${source.code} episode=${episode.name}", throwable)
            ""
        }
        if (playUrl.isBlank()) return false

        currentSourceIndex = targetSourceIndex
        currentEpisodeIndex = safeEpisode
        beginPlayback(playUrl)
        return true
    }

    suspend fun tryNextSource(): Boolean {
        if (movieId.isBlank() || sources.isEmpty()) return false
        for (offset in 1 until sources.size) {
            val index = (currentSourceIndex + offset) % sources.size
            loadingMessage = "当前来源暂时不可用，正在尝试其他来源..."
            if (playSourceEpisode(index, currentEpisodeIndex)) {
                return true
            }
        }
        return false
    }

    LaunchedEffect(movieId, sources) {
        if (movieId.isBlank() || sources.isEmpty() || initialPlaybackTriggered) return@LaunchedEffect
        initialPlaybackTriggered = true
        val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
        if (!started) {
            val switched = tryNextSource()
            if (!switched) {
                waitingForPlayableSource = false
                loadingMessage = null
                errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
            }
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> {
                        waitingForPlayableSource = false
                        if (loadingMessage != null) {
                            loadingMessage = null
                        }
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> {
                        if (loadingMessage.isNullOrBlank()) {
                            loadingMessage = "正在加载视频..."
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "playback error: ${error.errorCodeName}", error)
                if (retryInProgress) return
                retryInProgress = true
                waitingForPlayableSource = false
                scope.launch {
                    val switched = tryNextSource()
                    retryInProgress = false
                    if (!switched) {
                        loadingMessage = null
                        errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
                    }
                }
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
            controller.release()
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            playPauseFocusRequester.requestFocus()
        }
    }

    BackHandler { onBack() }

    LaunchedEffect(controlsTick) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(playAttemptToken) {
        if (playAttemptToken == 0 || !waitingForPlayableSource) return@LaunchedEffect
        val attemptToken = playAttemptToken
        delay(SOURCE_LOAD_TIMEOUT_MS)
        if (!waitingForPlayableSource || playAttemptToken != attemptToken || retryInProgress) {
            return@LaunchedEffect
        }

        retryInProgress = true
        loadingMessage = "当前来源加载较慢，正在尝试其他来源..."
        val switched = tryNextSource()
        retryInProgress = false
        if (!switched && playAttemptToken == attemptToken) {
            waitingForPlayableSource = false
            loadingMessage = null
            errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
        }
    }

    LaunchedEffect(movieId, showControls) {
        while (isActive) {
            positionMs = controller.currentPositionMs()
            durationMs = controller.durationMs()
            isPlaying = controller.isPlaying()
            delay(if (showControls) 250L else 900L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        controller.seekBy(-10_000)
                        flashControls()
                        true
                    }
                    Key.DirectionRight -> {
                        controller.seekBy(10_000)
                        flashControls()
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        flashControls()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.Spacebar -> {
                        controller.togglePlayPause()
                        flashControls()
                        true
                    }
                    Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = controller.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loadingMessage != null || isBuffering) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xCC101010),
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                    )
                    Text(
                        text = loadingMessage ?: "正在加载视频...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xDD111111),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "你可以返回重新选择其它来源，或稍后再试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerControlButton(
                            text = "返回",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "重试",
                            onClick = {
                                errorMessage = null
                                scope.launch {
                                    loadingMessage = "正在重新尝试播放..."
                                    val started = playSourceEpisode(currentSourceIndex, currentEpisodeIndex)
                                    if (!started) {
                                        val switched = tryNextSource()
                                        if (!switched) {
                                            waitingForPlayableSource = false
                                            loadingMessage = null
                                            errorMessage = "这个视频暂时无法播放，请稍后再试其它片源。"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 22.dp)
                    .focusable(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xE51A1A1A),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title.ifBlank { "视频播放" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isBuffering) "加载中" else if (isPlaying) "播放中" else "已暂停",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }

                    Text(
                        text = buildPlaybackLabel(positionMs, durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.82f),
                    )

                    LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )

                    Text(
                        text = "左右键快进后退，确认键播放暂停，返回键退出",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PlayerControlButton(
                            text = "返回",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = if (isPlaying) "暂停" else "播放",
                            onClick = {
                                controller.togglePlayPause()
                                flashControls()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(playPauseFocusRequester),
                            selected = isPlaying,
                        )
                        PlayerControlButton(
                            text = "后退30秒",
                            onClick = {
                                controller.seekBy(-30_000)
                                flashControls()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerControlButton(
                            text = "前进30秒",
                            onClick = {
                                controller.seekBy(30_000)
                                flashControls()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val containerColor = when {
        selected || focused -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF2B2B2B)
    }
    val borderColor = when {
        focused -> TvFocusBorder
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = Color.White,
        tonalElevation = if (focused) 4.dp else 0.dp,
        modifier = modifier
            .height(48.dp)
            .border(2.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildPlaybackLabel(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return formatTime(positionMs)
    return "${formatTime(positionMs)} / ${formatTime(durationMs)}"
}

private fun formatTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
