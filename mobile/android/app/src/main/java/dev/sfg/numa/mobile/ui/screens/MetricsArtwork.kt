package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.catalog.CatalogRepository
import dev.sfg.numa.mobile.model.MetricsCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal object MetricsArtwork {
    private val gate = Semaphore(2)
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
    }
    private suspend fun lookup(block: suspend () -> String): String = try {
        withTimeoutOrNull(10_000) { block() }.orEmpty()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { "" }

    suspend fun resolve(row: MetricsCollection, catalog: CatalogRepository): String {
        if (row.artworkUrl.isNotBlank()) return row.artworkUrl
        synchronized(cache) { cache[row.key]?.let { return it } }
        val url = try {
            gate.withPermit {
                synchronized(cache) { cache[row.key]?.let { return it } }
                val direct = if (row.browseId.isNotBlank()) lookup { catalog.browse(row.browseId).artworkUrl } else ""
                direct.ifBlank {
                    lookup {
                        if (row.artist) catalog.search(row.name, "artists", guest = true).artists
                            .firstOrNull { it.name.equals(row.name, ignoreCase = true) }?.artworkUrl.orEmpty()
                        else catalog.search("${row.name} ${row.performer}".trim(), "albums", guest = true).albums
                            .firstOrNull { it.title.equals(row.name, ignoreCase = true) &&
                                (row.performer.isBlank() || it.artist.equals(row.performer, ignoreCase = true)) }?.artworkUrl.orEmpty()
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "" }
        if (url.isNotBlank()) synchronized(cache) { cache[row.key] = url }
        return url
    }
}
