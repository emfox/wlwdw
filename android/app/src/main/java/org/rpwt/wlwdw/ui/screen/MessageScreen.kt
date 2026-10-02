package org.rpwt.wlwdw.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.model.Message
import org.rpwt.wlwdw.data.model.MessageDay
import org.rpwt.wlwdw.ui.component.MessageRow
import org.rpwt.wlwdw.ui.component.StatusPill
import org.rpwt.wlwdw.ui.component.StatusTone
import org.rpwt.wlwdw.ui.theme.WlwdwDimens

/**
 * Tab 3: received messages.
 *
 * The list is a `LazyColumn` rather than the old `ListView` +
 * `SimpleCursorAdapter`, which is what the design wants for a structural
 * reason: with a cursor, the leak is fixed by remembering to close it, and with
 * a list of values it cannot happen at all.
 *
 * The tracking chip in the bar is the same one the map floats, because the
 * tracking state has to stay visible from every tab -- the status screen itself
 * is the only place it would be noise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    messages: List<Message>,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Grouped here rather than in the query: the list is already in arrival
    // order, so one pass over it gives both the groups and their order, and a
    // third "earlier" bucket is a one-line change instead of another query.
    val groups = MessageDay.entries.mapNotNull { day ->
        messages.filter { it.day == day }.takeIf { it.isNotEmpty() }?.let { day to it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_tab_messages),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = {
                StatusPill(
                    text = stringResource(R.string.wlwdw_ring_reporting),
                    tone = StatusTone.Ok,
                    modifier = Modifier.padding(end = 2.dp),
                )
                IconButton(onClick = onMarkAllRead) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = stringResource(R.string.wlwdw_messages_mark_all_read),
                    )
                }
            },
        )

        if (groups.isEmpty()) {
            EmptyMessages(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WlwdwDimens.PagePadding,
                    end = WlwdwDimens.PagePadding,
                    top = WlwdwDimens.CardGap,
                    bottom = WlwdwDimens.CardGap,
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                groups.forEach { (day, dayMessages) ->
                    item(key = "day-$day") {
                        DaySeparator(stringResource(dayLabel(day)))
                    }
                    items(dayMessages, key = { it.id }) { message ->
                        MessageRow(
                            sender = message.sender,
                            body = message.body,
                            time = message.time,
                            unread = message.unread,
                            fromServer = message.fromServer,
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun dayLabel(day: MessageDay): Int = when (day) {
    MessageDay.Today -> R.string.wlwdw_messages_today
    MessageDay.Yesterday -> R.string.wlwdw_messages_yesterday
    MessageDay.Earlier -> R.string.wlwdw_messages_earlier
}

/** A date break in the list: a word with a rule either side. */
@Composable
private fun DaySeparator(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Rule(Modifier.weight(1f))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Rule(Modifier.weight(1f))
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun EmptyMessages(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(WlwdwDimens.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Text(
            text = stringResource(R.string.wlwdw_messages_empty_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.wlwdw_messages_empty_body),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
