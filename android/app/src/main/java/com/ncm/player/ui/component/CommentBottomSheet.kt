package com.ncm.player.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ncm.player.R
import com.ncm.player.model.Comment
import kotlinx.coroutines.delay

@Composable
fun CommentAnimatedItem(
    index: Int,
    comment: Comment,
    onLikeClick: (Comment) -> Unit,
    onReplyClick: (Comment) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onViewFloorClick: (Comment) -> Unit = {},
    shouldAnimate: Boolean = true,
    hasAnimatedBefore: Boolean = false,
    onAnimated: () -> Unit = {}
) {
    var visible by remember(comment.id) { mutableStateOf(!shouldAnimate || hasAnimatedBefore) }
    if (shouldAnimate && !hasAnimatedBefore) {
        LaunchedEffect(comment.id) {
            delay(index * 40L)
            visible = true
            onAnimated()
        }
    }

    val enterTransition = if (shouldAnimate) {
        fadeIn(animationSpec = tween(400)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(400, easing = EaseOutQuart)
        )
    } else {
        EnterTransition.None
    }

    AnimatedVisibility(
        visible = visible,
        enter = enterTransition
    ) {
        CommentItem(
            comment = comment,
            onLikeClick = { onLikeClick(comment) },
            onReplyClick = { onReplyClick(comment) },
            onAvatarClick = { onAvatarClick(comment.userId) },
            onViewFloorClick = { onViewFloorClick(comment) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    hotComments: List<Comment>,
    newestComments: List<Comment>,
    totalCount: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    currentSort: Int = 1,
    onLoadMore: () -> Unit,
    onLikeClick: (Comment) -> Unit,
    onReplyClick: (Comment) -> Unit,
    onPostComment: (String) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onSortChange: (Int) -> Unit = {},
    onViewFloorClick: (Comment) -> Unit = {},
    onDismiss: () -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val animatedIds = rememberSaveable { mutableStateSetOf<Long>() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= (hotComments.size + newestComments.size) - 5) {
                    if (hasMore && !isLoading) {
                        onLoadMore()
                    }
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            // Header with total count and sort
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.comments_count, totalCount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(when(currentSort) {
                                    1 -> R.string.sort_recommend
                                    2 -> R.string.sort_hot
                                    else -> R.string.sort_time
                                }),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row {
                            val sorts = listOf(
                                1 to R.string.sort_recommend,
                                2 to R.string.sort_hot,
                                3 to R.string.sort_time
                            )
                            sorts.forEach { (type, labelRes) ->
                                FilterChip(
                                    selected = currentSort == type,
                                    onClick = { onSortChange(type) },
                                    label = { Text(stringResource(labelRes)) },
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Comment List
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    if (hotComments.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.hot_comments),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        itemsIndexed(hotComments, key = { _, c -> "hot_${c.id}" }) { index, comment ->
                            CommentAnimatedItem(
                                index = index,
                                comment = comment,
                                onLikeClick = onLikeClick,
                                onReplyClick = onReplyClick,
                                onAvatarClick = onAvatarClick,
                                shouldAnimate = index < 15,
                                hasAnimatedBefore = animatedIds.contains(comment.id),
                                onViewFloorClick = onViewFloorClick,
                                onAnimated = { animatedIds.add(comment.id) }
                            )
                        }
                    }

                    if (newestComments.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (currentSort == 2) stringResource(R.string.sort_hot) else stringResource(R.string.newest_comments),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        itemsIndexed(newestComments, key = { _, c -> "new_${c.id}" }) { index, comment ->
                            CommentAnimatedItem(
                                index = index % 10,
                                comment = comment,
                                onLikeClick = onLikeClick,
                                onReplyClick = onReplyClick,
                                onAvatarClick = onAvatarClick,
                                shouldAnimate = index < 15,
                                hasAnimatedBefore = animatedIds.contains(comment.id),
                                onViewFloorClick = onViewFloorClick,
                                onAnimated = { animatedIds.add(comment.id) }
                            )
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                WavyCircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (!hasMore && (newestComments.isNotEmpty() || hotComments.isNotEmpty())) {
                        item {
                            Text(
                                text = stringResource(R.string.no_more_comments),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Bottom fade for the list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                            )
                        )
                )
            }

            // Input field
            Surface(
                tonalElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.add_comment)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FloatingActionButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onPostComment(commentText)
                                commentText = ""
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
