package org.crux.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import org.crux.R
import org.crux.data.ai.scribe.CarouselReview
import org.crux.ui.component.FAIcon

internal enum class CarouselReviewStatus {
    Reviewing,
    Done,
    NoChanges,
    ModelMissing,
    Failed,
}

/**
 * Shows the on-device model's review of the rule-built carousel: overall direction, specific
 * notes, and how many slides it fixed, with Undo / Apply so the rule version is always one tap away.
 */
@Composable
internal fun CarouselAiReviewCard(
    status: CarouselReviewStatus,
    review: CarouselReview?,
    fixesApplied: Boolean,
    onUndoFixes: () -> Unit,
    onApplyFixes: () -> Unit,
    onReviewAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status == CarouselReviewStatus.Reviewing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.carousel_ai_review_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val message =
                when (status) {
                    CarouselReviewStatus.Reviewing -> stringResource(R.string.carousel_ai_review_running)
                    CarouselReviewStatus.NoChanges -> stringResource(R.string.carousel_ai_review_no_changes)
                    CarouselReviewStatus.ModelMissing -> stringResource(R.string.carousel_ai_review_model_missing)
                    CarouselReviewStatus.Failed -> stringResource(R.string.carousel_ai_review_failed)
                    CarouselReviewStatus.Done -> null
                }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (status == CarouselReviewStatus.Done && review != null) {
                if (review.direction.isNotBlank()) {
                    Text(
                        text = review.direction,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                review.notes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (review.fixes.isNotEmpty()) {
                    Text(
                        text =
                            if (fixesApplied) {
                                pluralStringResource(R.plurals.carousel_ai_review_fixed, review.fixes.size, review.fixes.size)
                            } else {
                                pluralStringResource(R.plurals.carousel_ai_review_fixes_ready, review.fixes.size, review.fixes.size)
                            },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (status == CarouselReviewStatus.Done && review != null && review.fixes.isNotEmpty()) {
                    if (fixesApplied) {
                        TextButton(onClick = onUndoFixes) { Text(stringResource(R.string.carousel_ai_review_undo)) }
                    } else {
                        TextButton(onClick = onApplyFixes) { Text(stringResource(R.string.carousel_ai_review_apply)) }
                    }
                }
                if (status != CarouselReviewStatus.Reviewing && status != CarouselReviewStatus.ModelMissing) {
                    TextButton(onClick = onReviewAgain) { Text(stringResource(R.string.carousel_ai_review_again)) }
                }
            }
        }
    }
}
