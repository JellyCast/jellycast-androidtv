package nl.jellycast.mobile.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R

/** The launcher brand above the wordmark: "Jelly" bold, "Cast" light in the accent. */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Jelly") }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                ) { append("Cast") }
            },
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
