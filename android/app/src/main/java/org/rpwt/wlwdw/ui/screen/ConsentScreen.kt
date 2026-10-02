package org.rpwt.wlwdw.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.theme.statusColors

/**
 * First run: what the app collects, in a full page rather than a dialog.
 *
 * The logic behind this step does not change -- location is only initialised
 * once the user agrees -- but the form does, and the design asks for the change
 * for a reason: this is the app's whole compliance precondition, and a dialog
 * whose body is boilerplate is easy to tap through. A page can say what is
 * collected, what is not, when it starts and how to stop it.
 *
 * Declining exits the app rather than looping on a non-cancellable dialog;
 * forced consent is not a defensible position, and it is exactly the pattern
 * that gets an app reported.
 */
@Composable
fun ConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(74.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.wlwdw_app_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.wlwdw_consent_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(26.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConsentPoint(stringResource(R.string.wlwdw_consent_point_location))
            ConsentPoint(stringResource(R.string.wlwdw_consent_point_contacts))
            ConsentPoint(stringResource(R.string.wlwdw_consent_point_background))
            ConsentPoint(stringResource(R.string.wlwdw_consent_point_stop))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onAgree,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.wlwdw_consent_agree),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight(660),
            )
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.wlwdw_consent_decline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.wlwdw_consent_fine),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConsentPoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .background(MaterialTheme.statusColors.okContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.statusColors.ok,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
