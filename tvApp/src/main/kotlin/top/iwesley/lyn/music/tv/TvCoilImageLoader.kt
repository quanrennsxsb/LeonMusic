package top.iwesley.lyn.music.tv

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache

@Composable
internal fun ConfigureTvImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(TV_IMAGE_MEMORY_CACHE_MAX_SIZE_BYTES)
                    .build()
            }
            .diskCache(null)
            .build()
    }
}
