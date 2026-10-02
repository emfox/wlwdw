package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.theme.WlwdwDimens

/**
 * The body of a screen that is not built yet.
 *
 * This stage delivers a buildable skeleton -- the theme, the navigation and the
 * bottom bar are real, the screens are not. Each one renders this until the
 * next stage fills it in, so that an unbuilt screen reads as unbuilt rather
 * than as a bug.
 */
@Composable
internal fun ScreenPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(WlwdwDimens.PagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.wlwdw_placeholder_not_implemented),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
