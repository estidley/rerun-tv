package com.rerun.tv.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Decorative identity beside the channel's readable name and number. */
@Composable
fun ChannelIcon(channelId: String, name: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    if (channelId.startsWith("music:")) MusicGenreIcon(channelId, name, modifier.size(size))
    else Image(painterResource(channelIconResource(channelId)), contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun ChannelWordmark(channelId: String, name: String, modifier: Modifier = Modifier) {
    if (channelId.startsWith("music:")) MusicGenreWordmark(channelId, name, modifier)
    else Image(
        painterResource(channelWordmarkResource(channelId)),
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    )
}
