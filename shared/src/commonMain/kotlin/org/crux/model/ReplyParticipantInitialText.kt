package org.crux.model

import org.crux.data.datasource.microblog.ComposeType
import org.crux.ui.render.RenderContent
import org.crux.ui.render.RenderRun
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ComposeInitialTextContext.resolveReplyParticipantInitialText(): InitialText? {
    if (composeType != ComposeType.Reply) return null

    val handleToAdd = mutableSetOf<String>()
    if (post.user?.key != selectedAccountKey) {
        post.user?.handle?.let {
            handleToAdd.add(it.canonical)
        }
    }
    post.content.original.renderRuns
        .asSequence()
        .flatMap { content ->
            when (content) {
                is RenderContent.BlockImage -> emptySequence()
                is RenderContent.Text -> content.runs.asSequence()
            }
        }.mapNotNull { run ->
            when (run) {
                is RenderRun.Image -> {
                    null
                }

                is RenderRun.Text -> {
                    val href = run.style.link ?: return@mapNotNull null
                    val isMentionScheme =
                        href.startsWith("crux://ProfileWithNameAndHost") || href.startsWith("flare://ProfileWithNameAndHost")
                    if (!isMentionScheme) {
                        return@mapNotNull null
                    }
                    MentionLink(
                        href = href,
                        text = run.text,
                    )
                }
            }
        }.filterNot { mention ->
            val schemePrefix =
                if (mention.href.startsWith(
                        "crux://",
                    )
                ) {
                    "crux://ProfileWithNameAndHost/"
                } else {
                    "flare://ProfileWithNameAndHost/"
                }
            val params =
                mention.href
                    .substringAfter(schemePrefix)
                    .substringBefore("?accountKey=")
                    .split('/')
            val userName = params.getOrNull(0)
            val host = params.getOrNull(1)
            currentUserHandle.canonical == "@$userName@$host"
        }.filterNot { mention ->
            mention.text == post.user?.handle?.canonical
        }.forEach { mention ->
            handleToAdd.add(mention.text)
        }
    val text =
        buildString {
            handleToAdd.forEach {
                append("$it ")
            }
        }
    return InitialText(
        text = text,
        cursorPosition = text.length,
    )
}

private data class MentionLink(
    val href: String,
    val text: String,
)
