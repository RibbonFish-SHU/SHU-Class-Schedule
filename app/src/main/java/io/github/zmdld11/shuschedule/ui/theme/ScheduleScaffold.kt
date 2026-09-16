package io.github.zmdld11.shuschedule.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.zmdld11.shuschedule.R
import io.github.zmdld11.shuschedule.data.settings.AppTheme

/** Local, decorative artwork. A dark scrim keeps even empty grid cells readable. */
@Composable
fun ScheduleScaffold(topBar: @Composable () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = topBar) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (LocalScheduleStyle.current.theme == AppTheme.ARKNIGHTS) {
                Image(
                    painter = painterResource(R.drawable.arknights_background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f)))
            }
            content()
        }
    }
}
