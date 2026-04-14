package com.ncm.player.ui.component

import com.ncm.player.ui.component.WavyCircularProgressIndicator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                if (index != null && index >= hotComments.size + newestComments.size - 5) {
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
        modifier = Modifier.fillMaxHeight(0.9f),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.comments_count, totalCount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sorts = listOf(
                        1 to R.string.sort_recommend,
                        2 to R.string.sort_hot,
                        3 to R.string.sort_time
                    )
                    sorts.forEach { (type, labelRes) ->
                        TextButton(
                            onClick = { onSortChange(type) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (currentSort == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                ) {
                    if (hotComments.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.hot_comments),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                onAnimated = { animatedIds.add(comment.id) }
                            )
                        }
                    }

                    if (newestComments.isNotEmpty()) {
                        item {
                            Text(
                                text = if (currentSort == 2) stringResource(R.string.sort_hot) else stringResource(R.string.newest_comments),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                onAnimated = { animatedIds.add(comment.id) }
                            )
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                WavyCircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (!hasMore && newestComments.isNotEmpty()) {
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
            }

            Surface(
                tonalElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.ime)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.add_comment)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onPostComment(commentText)
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank(),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
