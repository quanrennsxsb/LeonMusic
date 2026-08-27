package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.Track

/** Creates a one-off shuffled queue without changing the source collection. */
fun shuffledPlaybackQueue(tracks: List<Track>): List<Track> = tracks.shuffled()
