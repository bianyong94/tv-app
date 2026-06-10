package com.globalvision.tv.feature.detail

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.verticalScroll
import android.util.Log
import kotlinx.coroutines.launch
import com.globalvision.tv.core.model.TvPosterItem
import com.globalvision.tv.core.model.TvMovieDetail
import com.globalvision.tv.core.model.TvEpisode
import com.globalvision.tv.core.network.TvRepository
import com.globalvision.tv.feature.common.TvFocusChip
import com.globalvision.tv.feature.common.TvPosterCard
import com.globalvision.tv.feature.common.TvScreenScaffold

@Composable
fun DetailScreen(
    repository: TvRepository,
    movieId: String,
    onBack: () -> Unit,
    onPlay: (title: String, movieId: String, sourceIndex: Int, episodeIndex: Int) -> Unit,
) {
    val tag = "DetailScreen"
    var detail by remember { mutableStateOf<TvMovieDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSourceIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    var selectedEpisodeIndex by rememberSaveable(movieId) { mutableStateOf(0) }
    val episodesBySource = remember(movieId) { mutableStateMapOf<String, List<TvEpisode>>() }
    var episodesLoading by remember(movieId) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(movieId) {
        Log.d(tag, "load detail start: movieId=$movieId")
        loading = true
        detail = try {
            repository.getDetail(movieId)
        } catch (_: Throwable) {
            Log.e(tag, "load detail failed: movieId=$movieId")
            null
        }
        loading = false
        Log.d(tag, "load detail end: movieId=$movieId hasDetail=${detail != null} sources=${detail?.sources?.size ?: 0}")
        selectedSourceIndex = 0
        selectedEpisodeIndex = 0
        episodesBySource.clear()
    }

    LaunchedEffect(detail) {
        if (detail != null) {
            playFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(detail?.id) {
        val movieDetail = detail ?: return@LaunchedEffect
        if (movieDetail.sources.isEmpty()) return@LaunchedEffect

        episodesLoading = true
        var selectedIndex = 0
        var selectedEpisodes = movieDetail.sources.firstOrNull()?.episodes.orEmpty()

        for ((index, source) in movieDetail.sources.withIndex()) {
            val fetchedEpisodes = try {
                repository.getEpisodes(movieDetail.id, source.code)
            } catch (throwable: Throwable) {
                Log.w(tag, "bootstrap episodes failed: movieId=${movieDetail.id} source=${source.code}", throwable)
                source.episodes
            }
            val candidateEpisode = fetchedEpisodes.firstOrNull() ?: continue
            val candidateUrl = repository.resolveEpisodeUrl(candidateEpisode)
            val playable = repository.isPlayableMediaUrl(candidateUrl)
            Log.d(
                tag,
                "bootstrap source check: index=$index code=${source.code} name=${source.name} episodes=${fetchedEpisodes.size} playable=$playable url=${candidateUrl.take(64)}",
            )
            if (playable) {
                selectedIndex = index
                selectedEpisodes = fetchedEpisodes
                break
            }
            if (index == 0) {
                selectedEpisodes = fetchedEpisodes
            }
        }

        selectedSourceIndex = selectedIndex
        selectedEpisodeIndex = 0
        if (movieDetail.sources.isNotEmpty()) {
            episodesBySource[movieDetail.sources[selectedIndex].code] = selectedEpisodes
        }
        episodesLoading = false
        Log.d(
            tag,
            "bootstrap source selected: movieId=${movieDetail.id} selectedIndex=$selectedIndex episodes=${selectedEpisodes.size}",
        )
    }

    if (loading && detail == null) {
        TvScreenScaffold(title = "详情", onBack = onBack) {
            CircularProgressIndicator()
            Text("加载详情中...", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    val movie = detail
    if (movie == null) {
        TvScreenScaffold(title = "详情", onBack = onBack) {
            Text("详情加载失败", style = MaterialTheme.typography.headlineSmall)
            TvFocusChip(text = "返回", onClick = onBack)
        }
        return
    }

    val safeSourceIndex = selectedSourceIndex.coerceIn(
        0,
        (movie.sources.size - 1).coerceAtLeast(0),
    )
    val currentSource = movie.sources.getOrNull(safeSourceIndex)
    val sourceEpisodes = currentSource?.code?.let { episodesBySource[it] }.orEmpty()
        .ifEmpty { currentSource?.episodes.orEmpty() }
    val safeEpisodeIndex = selectedEpisodeIndex.coerceIn(
        0,
        (sourceEpisodes.size - 1).coerceAtLeast(0),
    )
    val currentEpisode = sourceEpisodes.getOrNull(safeEpisodeIndex)
    val playbackEpisode = currentEpisode
        ?: sourceEpisodes.firstOrNull()
        ?: currentSource?.episodes?.firstOrNull()
        ?: movie.sources.firstOrNull()?.episodes?.firstOrNull()
    val currentSourceEpisodesLoaded = currentSource?.code?.let { episodesBySource.containsKey(it) } == true

    BackHandler(onBack = onBack)

    LaunchedEffect(movie.id, currentSource?.code) {
        val source = currentSource ?: return@LaunchedEffect
        episodesLoading = true
        val fetched = try {
            repository.getEpisodes(movie.id, source.code)
        } catch (throwable: Throwable) {
            Log.w(tag, "load episodes failed: movieId=${movie.id} source=${source.code}", throwable)
            emptyList()
        }
        if (fetched.isNotEmpty()) {
            Log.d(
                tag,
                "load episodes end: movieId=${movie.id} source=${source.code} episodes=${fetched.size} firstReady=${fetched.firstOrNull()?.readyToPlay} firstPlayUrl=${fetched.firstOrNull()?.playUrl.orEmpty().take(48)}",
            )
            episodesBySource[source.code] = fetched
        } else {
            Log.d(
                tag,
                "load episodes end: movieId=${movie.id} source=${source.code} episodes=${source.episodes.size} fallbackDetailList=true",
            )
            episodesBySource[source.code] = source.episodes
        }
        selectedEpisodeIndex = 0
        episodesLoading = false
    }

    suspend fun playCurrentSelection() {
        val source = currentSource ?: run {
            Log.w(tag, "play requested but no source is selected")
            return
        }
        val episodes = episodesBySource[source.code].orEmpty().ifEmpty { source.episodes }
        if (episodes.isEmpty()) {
            Log.w(tag, "play requested but no episodes available for source=${source.code}")
            return
        }

        val episode = episodes.getOrNull(selectedEpisodeIndex.coerceIn(0, episodes.lastIndex))
            ?: episodes.firstOrNull()
            ?: run {
                Log.w(tag, "play requested but no episode can be resolved for source=${source.code}")
                return
            }

        Log.d(
            tag,
            "open player: movieId=${movie.id} title=${movie.title} source=${source.name} episode=${episode.name}",
        )
        onPlay(movie.title, movie.id, safeSourceIndex, selectedEpisodeIndex.coerceIn(0, episodes.lastIndex))
    }

    TvScreenScaffold(title = "", onBack = null, showTitle = false) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(18.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        TvPosterCard(
                            item = movie.toPosterItem(),
                            width = 280.dp,
                        ) {}

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(movie.title, style = MaterialTheme.typography.headlineLarge)
                            Text(
                                listOf(movie.year, movie.area, movie.remarks)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOfNotNull(
                                    movie.director.takeIf { it.isNotBlank() }?.let { "导演：$it" },
                                    movie.writer.takeIf { it.isNotBlank() }?.let { "编剧：$it" },
                                    movie.actor.takeIf { it.isNotBlank() }?.let { "主演：$it" },
                                ).joinToString("   "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                movie.content.ifBlank { "暂无简介" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                            )
                            Text(
                                text = "播放信息 · 源 ${safeSourceIndex + 1}/${movie.sources.size} · 集 ${safeEpisodeIndex + 1}/${sourceEpisodes.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (playbackEpisode == null && episodesLoading) {
                                Text(
                                    text = "正在加载可播放集数...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            TvFocusChip(
                                text = if (playbackEpisode != null) {
                                    "立即播放 · ${playbackEpisode.name}"
                                } else if (episodesLoading) {
                                    "立即播放 · 加载中"
                                } else {
                                    "立即播放"
                                },
                                selected = playbackEpisode != null,
                                modifier = Modifier
                                    .focusRequester(playFocusRequester)
                                    .focusProperties { down = sourceFocusRequester },
                                onClick = {
                                    coroutineScope.launch { playCurrentSelection() }
                                },
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "切换播放源",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = if (currentSource != null) {
                                    "当前源：${currentSource.name}"
                                } else {
                                    "暂无可用播放源"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (movie.sources.isEmpty()) {
                                Text("暂无可播放资源", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Row(
                                    modifier = Modifier.focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    movie.sources.forEachIndexed { index, source ->
                                        TvFocusChip(
                                            text = source.name,
                                            selected = index == safeSourceIndex,
                                            modifier = if (index == 0) {
                                                Modifier
                                                    .focusRequester(sourceFocusRequester)
                                                    .focusProperties { down = episodeFocusRequester }
                                            } else {
                                                Modifier.focusProperties { down = episodeFocusRequester }
                                            },
                                            onClick = {
                                                selectedSourceIndex = index
                                                selectedEpisodeIndex = 0
                                                coroutineScope.launch {
                                                    val cached = episodesBySource[source.code]
                                                    if (cached == null) {
                                                        episodesLoading = true
                                                        val fetched = try {
                                                            repository.getEpisodes(movie.id, source.code)
                                                        } catch (throwable: Throwable) {
                                                            Log.w(tag, "source switch load failed: movieId=${movie.id} source=${source.code}", throwable)
                                                            emptyList()
                                                        }
                                                        episodesBySource[source.code] = if (fetched.isNotEmpty()) fetched else source.episodes
                                                        episodesLoading = false
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("选集", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (currentEpisode != null) {
                        "当前选中：${currentEpisode.name}"
                    } else {
                        "当前选中：无"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val episodes = sourceEpisodes
                if (episodes.isEmpty()) {
                    Text(
                        if (episodesLoading) "加载选集中..." else "暂无选集",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .focusGroup(),
                    ) {
                        items(episodes) { episode ->
                            val index = episodes.indexOf(episode)
                            TvFocusChip(
                                text = episode.name,
                                selected = index == safeEpisodeIndex,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(episodeFocusRequester)
                                        .focusProperties { up = sourceFocusRequester }
                                } else {
                                    Modifier.focusProperties { up = sourceFocusRequester }
                                },
                                onClick = {
                                    selectedEpisodeIndex = index
                                    Log.d(
                                        tag,
                                        "episode selected: source=${currentSource?.name.orEmpty()} episode=${episode.name}",
                                    )
                                    onPlay("${movie.title} ${episode.name}", movie.id, safeSourceIndex, index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun TvMovieDetail.toPosterItem() = TvPosterItem(
    id = id,
    title = title,
    posterUrl = posterUrl,
    year = year,
    score = "",
    category = "",
    remark = remarks,
    overview = content,
)
