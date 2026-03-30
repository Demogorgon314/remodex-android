@file:OptIn(ExperimentalFoundationApi::class)

package com.emanueledipietro.remodex.feature.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emanueledipietro.remodex.R
import com.emanueledipietro.remodex.feature.appshell.AppUiState
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome

private const val ProjectPreviewCount = 10
private const val SidebarFooterTestTag = "sidebar_footer"
private const val SidebarNewChatButtonTag = "sidebar_new_chat_button"
private const val SidebarProjectNewChatButtonTagPrefix = "sidebar_project_new_chat_button_"
private const val SidebarRenameTextFieldTag = "sidebar_rename_text_field"
private val SidebarRowCornerRadius = 14.dp

private data class ThreadRenamePromptState(
    val threadId: String = "",
    val currentTitle: String = "",
    val draftTitle: String = "",
    val isPresented: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreen(
    uiState: AppUiState,
    onSelectThread: (String) -> Unit,
    onRefreshThreads: () -> Unit,
    onRetryConnection: () -> Unit,
    onCreateThread: (String?) -> Unit,
    onSetProjectGroupCollapsed: (String, Boolean) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    onArchiveProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    var isNewChatSheetPresented by rememberSaveable { mutableStateOf(false) }
    var renamePromptState by remember {
        mutableStateOf(ThreadRenamePromptState())
    }
    var expandedProjectIds by remember { mutableStateOf(setOf<String>()) }
    var knownProjectGroupIds by remember { mutableStateOf(setOf<String>()) }
    var hasInitializedProjectExpansion by rememberSaveable { mutableStateOf(false) }
    var revealedProjectGroupIds by remember { mutableStateOf(setOf<String>()) }
    var expandedSubagentParentIds by remember { mutableStateOf(setOf<String>()) }

    val groups = remember(uiState.threads, searchText) {
        SidebarThreadGrouping.makeGroups(
            threads = uiState.threads,
            query = searchText,
        )
    }
    val projectGroups = remember(groups) {
        groups.filter { group -> group.kind == SidebarThreadGroupKind.PROJECT }
    }
    val newChatProjectGroups = remember(uiState.threads) {
        SidebarThreadGrouping.makeGroups(
            threads = uiState.threads,
            query = "",
        ).filter { group ->
            group.kind == SidebarThreadGroupKind.PROJECT && !group.projectPath.isNullOrBlank()
        }
    }
    val projectGroupIds = remember(projectGroups) { projectGroups.map(SidebarThreadGroup::id).toSet() }
    val selectedThreadId = uiState.selectedThread?.id
    val selectedProjectGroupId = remember(groups, uiState.selectedThread) {
        SidebarProjectExpansionState.groupIdContainingSelectedThread(
            selectedThread = uiState.selectedThread,
            groups = groups,
        )
    }
    val selectedSubagentAncestorIds = remember(uiState.threads, selectedThreadId) {
        selectedSubagentAncestorIds(
            threads = uiState.threads,
            selectedThreadId = selectedThreadId,
        )
    }

    LaunchedEffect(projectGroupIds, uiState.collapsedProjectGroupIds) {
        val snapshot = SidebarProjectExpansionState.synchronizedState(
            currentExpandedGroupIds = expandedProjectIds,
            knownGroupIds = knownProjectGroupIds,
            visibleGroups = groups,
            hasInitialized = hasInitializedProjectExpansion,
            persistedCollapsedGroupIds = uiState.collapsedProjectGroupIds,
        )
        expandedProjectIds = snapshot.expandedGroupIds
        knownProjectGroupIds = snapshot.knownGroupIds
        hasInitializedProjectExpansion = true
        revealedProjectGroupIds = revealedProjectGroupIds.intersect(projectGroupIds)
    }

    LaunchedEffect(selectedProjectGroupId, uiState.collapsedProjectGroupIds) {
        selectedProjectGroupId?.let { groupId ->
            if (!SidebarProjectExpansionState.shouldAutoRevealSelectedGroup(groupId, uiState.collapsedProjectGroupIds)) {
                return@let
            }
            expandedProjectIds = expandedProjectIds + groupId
        }
    }

    LaunchedEffect(selectedSubagentAncestorIds) {
        if (selectedSubagentAncestorIds.isNotEmpty()) {
            expandedSubagentParentIds = expandedSubagentParentIds + selectedSubagentAncestorIds
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SidebarHeader(
            uiState = uiState,
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            onSearchActiveChange = onSearchActiveChange,
            onOpenNewChat = { isNewChatSheetPresented = true },
            onRetryConnection = onRetryConnection,
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshingThreads,
            onRefresh = onRefreshThreads,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
            ) {
                if (groups.isEmpty()) {
                    item {
                        EmptyThreadsState(
                            isConnected = uiState.isConnected,
                            isFiltering = searchText.isNotBlank(),
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item(key = group.id) {
                            when (group.kind) {
                                SidebarThreadGroupKind.PROJECT -> {
                                    ProjectGroupSection(
                                        group = group,
                                        selectedThreadId = selectedThreadId,
                                        expanded = expandedProjectIds.contains(group.id),
                                        revealAll = revealedProjectGroupIds.contains(group.id) ||
                                            group.threads.any { thread -> thread.id == selectedThreadId },
                                        expandedSubagentParentIds = expandedSubagentParentIds,
                                        onToggleExpanded = {
                                            val isCurrentlyExpanded = group.id in expandedProjectIds
                                            expandedProjectIds = expandedProjectIds.toMutableSet().apply {
                                                if (isCurrentlyExpanded) {
                                                    remove(group.id)
                                                } else {
                                                    add(group.id)
                                                }
                                            }
                                            if (isCurrentlyExpanded) {
                                                revealedProjectGroupIds = revealedProjectGroupIds - group.id
                                            }
                                            onSetProjectGroupCollapsed(group.id, isCurrentlyExpanded)
                                        },
                                        onToggleSubagentExpansion = { threadId ->
                                            expandedSubagentParentIds = expandedSubagentParentIds.toMutableSet().apply {
                                                if (!add(threadId)) {
                                                    remove(threadId)
                                                }
                                            }
                                        },
                                        onRevealAll = {
                                            revealedProjectGroupIds = revealedProjectGroupIds + group.id
                                        },
                                        canCreateThread = uiState.canCreateThread,
                                        isCreatingThread = uiState.isCreatingThread,
                                        onCreateThread = { onCreateThread(group.projectPath) },
                                        onArchiveProject = {
                                            group.projectPath?.let(onArchiveProject)
                                        },
                                        onSelectThread = onSelectThread,
                                        onRenameThread = { threadId, currentTitle ->
                                            renamePromptState = ThreadRenamePromptState(
                                                threadId = threadId,
                                                currentTitle = currentTitle,
                                                draftTitle = currentTitle,
                                                isPresented = true,
                                            )
                                        },
                                        onArchiveThread = onArchiveThread,
                                        onUnarchiveThread = onUnarchiveThread,
                                        onDeleteThread = onDeleteThread,
                                    )
                                }

                                SidebarThreadGroupKind.ARCHIVED -> {
                                    ArchivedSection(
                                        group = group,
                                        selectedThreadId = selectedThreadId,
                                        archivedExpanded = archivedExpanded,
                                        expandedSubagentParentIds = expandedSubagentParentIds,
                                        onArchivedExpandedChange = { archivedExpanded = it },
                                        onToggleSubagentExpansion = { threadId ->
                                            expandedSubagentParentIds = expandedSubagentParentIds.toMutableSet().apply {
                                                if (!add(threadId)) {
                                                    remove(threadId)
                                                }
                                            }
                                        },
                                        onSelectThread = onSelectThread,
                                        onRenameThread = { threadId, currentTitle ->
                                            renamePromptState = ThreadRenamePromptState(
                                                threadId = threadId,
                                                currentTitle = currentTitle,
                                                draftTitle = currentTitle,
                                                isPresented = true,
                                            )
                                        },
                                        onArchiveThread = onArchiveThread,
                                        onUnarchiveThread = onUnarchiveThread,
                                        onDeleteThread = onDeleteThread,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SidebarFooter(
            trustedMacName = uiState.trustedMac?.name,
            trustedMacTitle = if (uiState.isConnected) "Connected to Mac" else "Saved Mac",
            onOpenSettings = onOpenSettings,
        )
    }

    if (isNewChatSheetPresented) {
        SidebarNewChatSheet(
            projectGroups = newChatProjectGroups,
            canCreateThread = uiState.canCreateThread,
            isCreatingThread = uiState.isCreatingThread,
            onDismiss = { isNewChatSheetPresented = false },
            onCreateThread = { projectPath ->
                if (!uiState.canCreateThread || uiState.isCreatingThread) {
                    return@SidebarNewChatSheet
                }
                isNewChatSheetPresented = false
                onCreateThread(projectPath)
            },
        )
    }

    if (renamePromptState.isPresented) {
        ThreadRenameDialog(
            state = renamePromptState,
            onDismiss = {
                renamePromptState = renamePromptState.copy(isPresented = false)
            },
            onDraftChange = { updatedDraft ->
                renamePromptState = renamePromptState.copy(draftTitle = updatedDraft)
            },
            onRename = { trimmedTitle ->
                val targetThreadId = renamePromptState.threadId
                renamePromptState = renamePromptState.copy(isPresented = false)
                onRenameThread(targetThreadId, trimmedTitle)
            },
        )
    }
}

@Composable
private fun SidebarHeader(
    uiState: AppUiState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onOpenNewChat: () -> Unit,
    onRetryConnection: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(26.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = "Remodex",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
        }

        SidebarSearchField(
            text = searchText,
            onTextChange = onSearchTextChange,
            onSearchActiveChange = onSearchActiveChange,
        )

        SidebarNewChatButton(
            enabled = uiState.canCreateThread,
            isLoading = uiState.isCreatingThread,
            onClick = onOpenNewChat,
        )

        if (!uiState.isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = uiState.connectionHeadline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetryConnection) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun SidebarNewChatButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val isEnabled = enabled && !isLoading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SidebarNewChatButtonTag)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isEnabled) 1f else 0.35f)
            .combinedClickable(
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = if (isLoading) "Creating..." else "New Chat",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SidebarNewChatSheet(
    projectGroups: List<SidebarThreadGroup>,
    canCreateThread: Boolean,
    isCreatingThread: Boolean,
    onDismiss: () -> Unit,
    onCreateThread: (String?) -> Unit,
) {
    val chrome = remodexConversationChrome()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(top = 12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = chrome.panelSurfaceStrong,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(44.dp)
                                .size(width = 44.dp, height = 4.dp),
                            shape = CircleShape,
                            color = chrome.tertiaryText.copy(alpha = 0.35f),
                        ) {}
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.CenterStart),
                            shape = RoundedCornerShape(999.dp),
                            color = chrome.mutedSurface,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Close",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = chrome.titleText,
                                )
                            }
                        }

                        Text(
                            text = "Start new chat",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = chrome.titleText,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item {
                            Text(
                                text = "Choose a project for this chat.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = chrome.secondaryText,
                            )
                        }

                        if (projectGroups.isNotEmpty()) {
                            item {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "Local",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = chrome.titleText,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(26.dp),
                                        color = chrome.panelSurface,
                                    ) {
                                        Column {
                                            projectGroups.forEachIndexed { index, group ->
                                                SidebarNewChatProjectRow(
                                                    label = group.label,
                                                    iconSystemName = group.iconSystemName,
                                                    enabled = canCreateThread && !isCreatingThread,
                                                    onClick = { onCreateThread(group.projectPath) },
                                                )
                                                if (index < projectGroups.lastIndex) {
                                                    HorizontalDivider(
                                                        color = chrome.subtleBorder,
                                                        modifier = Modifier.padding(horizontal = 18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            SidebarNewChatCloudCard(
                                enabled = canCreateThread && !isCreatingThread,
                                onClick = { onCreateThread(null) },
                            )
                        }

                        item {
                            Text(
                                text = "Chats started in a project stay scoped to that working directory. If you pick Cloud, the chat is global.",
                                style = MaterialTheme.typography.bodySmall,
                                color = chrome.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarNewChatProjectRow(
    label: String,
    iconSystemName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SidebarSymbolIcon(
            symbolName = iconSystemName,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SidebarNewChatCloudCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val chrome = remodexConversationChrome()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = chrome.panelSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = sidebarSymbolImageVector("cloud"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Cloud",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Start a chat without a local working directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = chrome.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun SidebarSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(SidebarRowCornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = sidebarSymbolImageVector("magnifyingglass"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Search conversations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                                onSearchActiveChange(focusState.isFocused)
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                    )
                }
                if (text.isNotEmpty()) {
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = { onTextChange("") },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (isFocused) {
            TextButton(
                onClick = {
                    onTextChange("")
                    focusManager.clearFocus(force = true)
                    onSearchActiveChange(false)
                },
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ProjectGroupSection(
    group: SidebarThreadGroup,
    selectedThreadId: String?,
    expanded: Boolean,
    revealAll: Boolean,
    expandedSubagentParentIds: Set<String>,
    canCreateThread: Boolean,
    isCreatingThread: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleSubagentExpansion: (String) -> Unit,
    onRevealAll: () -> Unit,
    onCreateThread: () -> Unit,
    onArchiveProject: () -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
) {
    val rootThreads = remember(group.threads) { rootThreads(group.threads) }
    val visibleRoots = if (revealAll) rootThreads else rootThreads.take(ProjectPreviewCount)
    val childrenByParentId = remember(group.threads) {
        group.threads
            .filter { thread -> !thread.parentThreadId.isNullOrBlank() }
            .groupBy { thread -> thread.parentThreadId.orEmpty() }
    }
    var projectMenuExpanded by remember(group.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 52.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onToggleExpanded,
                        onLongClick = { projectMenuExpanded = true },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = sidebarSymbolImageVector(group.iconSystemName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ProjectHeaderActionButton(
                icon = Icons.Outlined.Add,
                contentDescription = "New conversation in ${group.label}",
                enabled = canCreateThread && !isCreatingThread,
                isLoading = isCreatingThread,
                onClick = onCreateThread,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("$SidebarProjectNewChatButtonTagPrefix${group.label}"),
            )

            DropdownMenu(
                expanded = projectMenuExpanded,
                onDismissRequest = { projectMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Archive Project") },
                    onClick = {
                        projectMenuExpanded = false
                        onArchiveProject()
                    },
                )
            }
        }

        if (expanded) {
            visibleRoots.forEach { thread ->
                ThreadTreeRow(
                    thread = thread,
                    selectedThreadId = selectedThreadId,
                    depth = 0,
                    childrenByParentId = childrenByParentId,
                    expandedSubagentParentIds = expandedSubagentParentIds,
                    onToggleSubagentExpansion = onToggleSubagentExpansion,
                    onSelectThread = onSelectThread,
                    onRenameThread = onRenameThread,
                    onArchiveThread = onArchiveThread,
                    onUnarchiveThread = onUnarchiveThread,
                    onDeleteThread = onDeleteThread,
                )
            }

            if (!revealAll && rootThreads.size > ProjectPreviewCount) {
                ShowMoreButton(
                    hiddenCount = rootThreads.size - ProjectPreviewCount,
                    onRevealAll = onRevealAll,
                )
            }
        }
    }
}

@Composable
private fun ProjectHeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.45f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = enabled,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    modifier = Modifier.size(12.dp),
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ShowMoreButton(
    hiddenCount: Int,
    onRevealAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onRevealAll) {
            Text("Show $hiddenCount more")
        }
    }
}

@Composable
private fun ArchivedSection(
    group: SidebarThreadGroup,
    selectedThreadId: String?,
    archivedExpanded: Boolean,
    expandedSubagentParentIds: Set<String>,
    onArchivedExpandedChange: (Boolean) -> Unit,
    onToggleSubagentExpansion: (String) -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
) {
    val childrenByParentId = remember(group.threads) {
        group.threads
            .filter { thread -> !thread.parentThreadId.isNullOrBlank() }
            .groupBy { thread -> thread.parentThreadId.orEmpty() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = { onArchivedExpandedChange(!archivedExpanded) },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = sidebarSymbolImageVector(group.iconSystemName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = group.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (archivedExpanded) 1f else 0.8f),
            )
        }

        if (archivedExpanded) {
            rootThreads(group.threads).forEach { thread ->
                ThreadTreeRow(
                    thread = thread,
                    selectedThreadId = selectedThreadId,
                    depth = 0,
                    childrenByParentId = childrenByParentId,
                    expandedSubagentParentIds = expandedSubagentParentIds,
                    onToggleSubagentExpansion = onToggleSubagentExpansion,
                    onSelectThread = onSelectThread,
                    onRenameThread = onRenameThread,
                    onArchiveThread = onArchiveThread,
                    onUnarchiveThread = onUnarchiveThread,
                    onDeleteThread = onDeleteThread,
                )
            }
        }
    }
}

@Composable
private fun ThreadTreeRow(
    thread: RemodexThreadSummary,
    selectedThreadId: String?,
    depth: Int,
    childrenByParentId: Map<String, List<RemodexThreadSummary>>,
    expandedSubagentParentIds: Set<String>,
    onToggleSubagentExpansion: (String) -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
) {
    val childThreads = childrenByParentId[thread.id].orEmpty()
    val expanded = expandedSubagentParentIds.contains(thread.id)

    ThreadRow(
        thread = thread,
        isSelected = selectedThreadId == thread.id,
        depth = depth,
        hasChildren = childThreads.isNotEmpty(),
        isExpanded = expanded,
        onToggleExpanded = if (childThreads.isNotEmpty()) {
            { onToggleSubagentExpansion(thread.id) }
        } else {
            null
        },
        onSelectThread = { onSelectThread(thread.id) },
        onRenameThread = onRenameThread,
        onArchiveThread = { onArchiveThread(thread.id) },
        onUnarchiveThread = { onUnarchiveThread(thread.id) },
        onDeleteThread = { onDeleteThread(thread.id) },
    )

    if (expanded) {
        childThreads.forEach { child ->
            ThreadTreeRow(
                thread = child,
                selectedThreadId = selectedThreadId,
                depth = depth + 1,
                childrenByParentId = childrenByParentId,
                expandedSubagentParentIds = expandedSubagentParentIds,
                onToggleSubagentExpansion = onToggleSubagentExpansion,
                onSelectThread = onSelectThread,
                onRenameThread = onRenameThread,
                onArchiveThread = onArchiveThread,
                onUnarchiveThread = onUnarchiveThread,
                onDeleteThread = onDeleteThread,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    thread: RemodexThreadSummary,
    isSelected: Boolean,
    depth: Int,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: (() -> Unit)?,
    onSelectThread: () -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: () -> Unit,
    onUnarchiveThread: () -> Unit,
    onDeleteThread: () -> Unit,
) {
    var menuExpanded by remember(thread.id) { mutableStateOf(false) }
    var menuOffset by remember(thread.id) { mutableStateOf(DpOffset.Zero) }
    val timingLabel = compactTimingLabel(thread.lastUpdatedLabel)
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(SidebarRowCornerRadius),
                )
                .pointerInput(thread.id, isSelected, hasChildren, density) {
                    detectTapGestures(
                        onTap = {
                            if (isSelected && hasChildren) {
                                onToggleExpanded?.invoke()
                            } else {
                                onSelectThread()
                            }
                        },
                        onLongPress = { pressOffset ->
                            menuOffset = with(density) {
                                DpOffset(
                                    x = pressOffset.x.toDp(),
                                    y = pressOffset.y.toDp(),
                                )
                            }
                            menuExpanded = true
                        },
                    )
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick {
                        if (isSelected && hasChildren) {
                            onToggleExpanded?.invoke()
                        } else {
                            onSelectThread()
                        }
                        true
                    }
                    onLongClick {
                        menuOffset = DpOffset.Zero
                        menuExpanded = true
                        true
                    }
                }
                .padding(horizontal = 12.dp, vertical = if (thread.isSubagent) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThreadLeadingIndicator(thread = thread)

            Text(
                text = threadDisplayTitle(thread),
                style = if (thread.isSubagent) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (thread.isSubagent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (thread.syncState == RemodexThreadSyncState.ARCHIVED_LOCAL) {
                    ThreadMetaBadge(
                        text = "Archived",
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    )
                }

                if (hasChildren && onToggleExpanded != null) {
                    IconButton(
                        modifier = Modifier.size(18.dp),
                        onClick = onToggleExpanded,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = if (isExpanded) {
                                "Collapse subagents"
                            } else {
                                "Expand subagents"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                timingLabel?.let { label ->
                    Text(
                        text = label,
                        style = if (thread.isSubagent) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            offset = menuOffset,
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRenameThread(thread.id, thread.displayTitle)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (thread.syncState == RemodexThreadSyncState.ARCHIVED_LOCAL) {
                            "Unarchive"
                        } else {
                            "Archive"
                        },
                    )
                },
                onClick = {
                    menuExpanded = false
                    if (thread.syncState == RemodexThreadSyncState.ARCHIVED_LOCAL) {
                        onUnarchiveThread()
                    } else {
                        onArchiveThread()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    onDeleteThread()
                },
            )
        }
    }
}

@Composable
private fun ThreadRenameDialog(
    state: ThreadRenamePromptState,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onRename: (String) -> Unit,
) {
    val trimmedDraft = state.draftTitle.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onRename(trimmedDraft) },
                enabled = trimmedDraft.isNotEmpty() && trimmedDraft != state.currentTitle.trim(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Rename Conversation") },
        text = {
            OutlinedTextField(
                value = state.draftTitle,
                onValueChange = onDraftChange,
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.testTag(SidebarRenameTextFieldTag),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
    )
}

@Composable
private fun ThreadLeadingIndicator(thread: RemodexThreadSummary) {
    Row(
        modifier = Modifier.width(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thread.isRunning && !thread.isSubagent) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
    }
}

@Composable
private fun ThreadMetaBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = Modifier
            .background(containerColor, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyThreadsState(
    isConnected: Boolean,
    isFiltering: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = when {
                isFiltering -> "No matching conversations"
                isConnected -> "No conversations"
                else -> "Connect to view conversations"
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (isFiltering) {
                "Try a different project name, thread title, or preview snippet."
            } else {
                "Your paired Mac will populate live and archived chats here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SidebarFooter(
    trustedMacName: String?,
    trustedMacTitle: String,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SidebarFooterTestTag)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenSettings) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = sidebarSymbolImageVector("gearshape.fill"),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!trustedMacName.isNullOrBlank()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trustedMacTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = trustedMacName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SidebarSymbolIcon(
    symbolName: String,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = sidebarSymbolImageVector(symbolName),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

private fun sidebarSymbolImageVector(symbolName: String): ImageVector {
    return when (symbolName) {
        "laptopcomputer" -> Icons.Outlined.Computer
        "arrow.triangle.branch" -> Icons.Outlined.AccountTree
        "archivebox" -> Icons.Outlined.Archive
        "magnifyingglass" -> Icons.Outlined.Search
        "gearshape.fill" -> Icons.Filled.Settings
        "cloud" -> Icons.Outlined.Cloud
        else -> Icons.Outlined.Folder
    }
}

private fun rootThreads(threads: List<RemodexThreadSummary>): List<RemodexThreadSummary> {
    val ids = threads.map(RemodexThreadSummary::id).toSet()
    return threads.filter { thread ->
        thread.parentThreadId.isNullOrBlank() || thread.parentThreadId !in ids
    }
}

private fun selectedSubagentAncestorIds(
    threads: List<RemodexThreadSummary>,
    selectedThreadId: String?,
): Set<String> {
    val selectedThread = threads.firstOrNull { thread -> thread.id == selectedThreadId } ?: return emptySet()
    val threadsById = threads.associateBy(RemodexThreadSummary::id)
    val ancestorIds = mutableSetOf<String>()
    var parentThreadId = selectedThread.parentThreadId

    while (!parentThreadId.isNullOrBlank() && ancestorIds.add(parentThreadId)) {
        parentThreadId = threadsById[parentThreadId]?.parentThreadId
    }

    return ancestorIds
}

private fun compactTimingLabel(lastUpdatedLabel: String): String? {
    return when {
        lastUpdatedLabel == "Updated just now" -> "now"
        lastUpdatedLabel == "Updated yesterday" -> "1d"
        lastUpdatedLabel.startsWith("Updated ") && lastUpdatedLabel.endsWith(" ago") -> {
            lastUpdatedLabel.removePrefix("Updated ").removeSuffix(" ago")
        }
        lastUpdatedLabel.startsWith("Updated ") -> {
            lastUpdatedLabel.removePrefix("Updated ")
        }
        lastUpdatedLabel.isBlank() -> null
        else -> lastUpdatedLabel
    }
}

private fun threadDisplayTitle(thread: RemodexThreadSummary): String {
    return thread.displayTitle
}
