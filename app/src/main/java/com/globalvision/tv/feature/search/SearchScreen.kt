package com.globalvision.tv.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.globalvision.tv.core.model.TvPosterItem
import com.globalvision.tv.core.network.TvRepository
import com.globalvision.tv.feature.common.TvFocusChip
import com.globalvision.tv.feature.common.TvPosterCard
import com.globalvision.tv.feature.common.TvScreenScaffold
import com.globalvision.tv.ui.theme.TvFocusBorder
import kotlinx.coroutines.flow.distinctUntilChanged

private const val SEARCH_PAGE_SIZE = 30

private enum class SearchFocusArea {
    SearchField,
    Results,
}

private data class CachedSearchContent(
    val keyword: String,
    val items: List<TvPosterItem>,
    val nextPage: Int,
    val total: Int,
    val hasMore: Boolean,
)

@Composable
fun SearchScreen(
    repository: TvRepository,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var searchToken by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<TvPosterItem>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var focusArea by remember { mutableStateOf(SearchFocusArea.SearchField) }
    var pendingFocusFirstResult by remember { mutableStateOf(false) }
    val searchCache = remember { mutableStateMapOf<String, CachedSearchContent>() }

    val searchFieldRequester = remember { FocusRequester() }
    val searchButtonRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    fun applyCache(cache: CachedSearchContent) {
        results = cache.items
        totalCount = cache.total
        nextPage = cache.nextPage
        hasMore = cache.hasMore
    }

    fun triggerSearch(forceRefresh: Boolean = false) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return
        keyword = normalized

        if (!forceRefresh) {
            searchCache[normalized]?.let { cached ->
                applyCache(cached)
                pendingFocusFirstResult = cached.items.isNotEmpty()
                if (cached.items.isEmpty()) {
                    focusArea = SearchFocusArea.SearchField
                }
                searchToken = 0
                return
            }
        }

        searchToken += 1
        pendingFocusFirstResult = true
    }

    suspend fun loadMoreResults() {
        val normalized = keyword.trim()
        if (normalized.isBlank() || loading || loadingMore || !hasMore) return

        loadingMore = true
        val pageToLoad = nextPage
        val response = try {
            repository.search(normalized, pageToLoad)
        } catch (_: Throwable) {
            null
        }

        if (response != null) {
            val merged = results + response.items
            val more = merged.size < response.total && response.items.isNotEmpty()
            results = merged
            totalCount = response.total
            nextPage = pageToLoad + 1
            hasMore = more
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = merged,
                nextPage = nextPage,
                total = totalCount,
                hasMore = hasMore,
            )
        } else {
            hasMore = false
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) {
        searchFieldRequester.requestFocus()
    }

    LaunchedEffect(results, pendingFocusFirstResult) {
        if (pendingFocusFirstResult && results.isNotEmpty()) {
            firstResultRequester.requestFocus()
            focusArea = SearchFocusArea.Results
            pendingFocusFirstResult = false
        }
    }

    LaunchedEffect(gridState, results, hasMore, loading, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex >= maxOf(results.lastIndex - 5, 0) &&
                    hasMore &&
                    !loading &&
                    !loadingMore
                ) {
                    loadMoreResults()
                }
            }
    }

    BackHandler {
        if (focusArea == SearchFocusArea.Results) {
            searchFieldRequester.requestFocus()
            focusArea = SearchFocusArea.SearchField
        } else {
            onBack()
        }
    }

    TvScreenScaffold(
        title = "",
        onBack = null,
        showTitle = false,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("搜索影片") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvFocusBorder,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        focusedLabelColor = TvFocusBorder,
                        cursorColor = TvFocusBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                        .focusRequester(searchFieldRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusArea = SearchFocusArea.SearchField
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.DirectionCenter -> {
                                    triggerSearch()
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (results.isNotEmpty()) {
                                        firstResultRequester.requestFocus()
                                        focusArea = SearchFocusArea.Results
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        },
                    singleLine = true,
                )

                TvFocusChip(
                    text = "搜索",
                    modifier = Modifier
                        .focusRequester(searchButtonRequester)
                        .height(46.dp),
                    onClick = { triggerSearch(forceRefresh = true) },
                )

                if (keyword.isNotBlank()) {
                    TvFocusChip(
                        text = "清除",
                        modifier = Modifier.height(46.dp),
                        onClick = {
                            keyword = ""
                            results = emptyList()
                            totalCount = 0
                            nextPage = 1
                            hasMore = false
                            pendingFocusFirstResult = false
                            searchFieldRequester.requestFocus()
                            focusArea = SearchFocusArea.SearchField
                        },
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!loading && results.isEmpty() && keyword.isNotBlank()) {
                Text(
                    text = "没有找到匹配结果",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (results.isNotEmpty()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup(),
                ) {
                    itemsIndexed(results) { index, item ->
                        val isTopRow = index < 6
                        TvPosterCard(
                            item = item,
                            width = 148.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == 0) {
                                        Modifier.focusRequester(firstResultRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (isTopRow) {
                                        Modifier.focusProperties { up = searchFieldRequester }
                                    } else {
                                        Modifier
                                    },
                                )
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Back
                                    ) {
                                        searchFieldRequester.requestFocus()
                                        focusArea = SearchFocusArea.SearchField
                                        true
                                    } else {
                                        false
                                    }
                                },
                            onClick = { onOpenDetail(item.id) },
                        )
                    }

                    if (loadingMore) {
                        item(span = { GridItemSpan(6) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(searchToken) {
        if (searchToken == 0 || keyword.isBlank()) return@LaunchedEffect

        loading = true
        results = emptyList()
        totalCount = 0
        nextPage = 1
        hasMore = false

        val normalized = keyword.trim()
        val response = try {
            repository.search(normalized, 1)
        } catch (_: Throwable) {
            null
        }

        if (response != null) {
            results = response.items
            totalCount = response.total
            nextPage = 2
            hasMore = response.items.size < response.total && response.items.isNotEmpty()
            searchCache[normalized] = CachedSearchContent(
                keyword = normalized,
                items = results,
                nextPage = nextPage,
                total = totalCount,
                hasMore = hasMore,
            )
        }

        loading = false
        if (results.isEmpty()) {
            focusArea = SearchFocusArea.SearchField
        }
    }
}
