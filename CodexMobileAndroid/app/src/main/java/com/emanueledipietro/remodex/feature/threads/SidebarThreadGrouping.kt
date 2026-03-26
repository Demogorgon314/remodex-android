package com.emanueledipietro.remodex.feature.threads

import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexThreadSyncState
import java.util.Locale

enum class SidebarThreadGroupKind {
    PROJECT,
    ARCHIVED,
}

data class SidebarThreadGroup(
    val id: String,
    val label: String,
    val kind: SidebarThreadGroupKind,
    val projectPath: String? = null,
    val threads: List<RemodexThreadSummary>,
)

object SidebarThreadGrouping {
    fun makeGroups(
        threads: List<RemodexThreadSummary>,
        query: String,
    ): List<SidebarThreadGroup> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val filteredThreads = if (normalizedQuery.isEmpty()) {
            threads
        } else {
            threads.filter { thread ->
                buildString {
                    append(thread.displayTitle)
                    append(' ')
                    append(thread.preview)
                    append(' ')
                    append(thread.projectPath)
                }.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
        val orderByThreadId = filteredThreads
            .mapIndexed { index, thread -> thread.id to index }
            .toMap()

        val archivedThreads = filteredThreads
            .filter { it.syncState == RemodexThreadSyncState.ARCHIVED_LOCAL }
            .sortedBy { thread -> orderByThreadId[thread.id] ?: Int.MAX_VALUE }
        val liveThreads = filteredThreads
            .filter { it.syncState != RemodexThreadSyncState.ARCHIVED_LOCAL }

        val projectGroups = liveThreads
            .groupBy { thread ->
                thread.projectPath.trim().ifBlank { "cloud" }
            }
            .map { (projectKey, projectThreads) ->
                SidebarThreadGroup(
                    id = "project:$projectKey",
                    label = projectLabel(projectThreads.firstOrNull()?.projectPath.orEmpty()),
                    kind = SidebarThreadGroupKind.PROJECT,
                    projectPath = projectThreads.firstOrNull()?.projectPath,
                    threads = projectThreads.sortedBy { thread -> orderByThreadId[thread.id] ?: Int.MAX_VALUE },
                )
            }
            .sortedBy { group ->
                group.threads.minOfOrNull { thread -> orderByThreadId[thread.id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
            }

        return buildList {
            addAll(projectGroups)
            if (archivedThreads.isNotEmpty()) {
                add(
                    SidebarThreadGroup(
                        id = "archived",
                        label = "Archived (${archivedThreads.size})",
                        kind = SidebarThreadGroupKind.ARCHIVED,
                        threads = archivedThreads,
                    ),
                )
            }
        }
    }
}

private fun projectLabel(projectPath: String): String {
    val trimmed = projectPath.trim()
    if (trimmed.isBlank()) {
        return "Cloud"
    }
    return trimmed
        .substringAfterLast('/')
        .ifBlank { trimmed }
}
