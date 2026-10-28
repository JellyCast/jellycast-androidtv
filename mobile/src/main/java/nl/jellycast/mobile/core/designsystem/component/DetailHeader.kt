package nl.jellycast.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.jellycast.mobile.R

/** Backdrop with a fade into the background, the logo (or the title) and a back button. */
@Composable
fun DetailHeader(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().aspectRatio(LANDSCAPE_RATIO).heightIn(max = 420.dp)) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.5f),
                        0.3f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to background,
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(background.copy(alpha = 0.6f)),
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
            )
        }
        Box(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 90.dp),
                )
            } else {
                Text(title, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
