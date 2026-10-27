package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.CatalogSection
import dev.sfg.numa.mobile.model.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout

internal suspend fun refreshHomeContent(graph: NumaGraph, state: MutableStateFlow<LoadState<List<CatalogSection>>>) {
    state.value = LoadState.Loading
    if (!graph.networkMonitor.refresh()) {
        state.value = LoadState.Error("No hay una red con acceso a Internet. Comprueba Wi-Fi o datos móviles.")
        return
    }
    state.value = try {
        val sections = withTimeout(25_000) { graph.catalog.home().sections }
        if (sections.isEmpty()) LoadState.Empty("Todavía no hay recomendaciones disponibles.") else LoadState.Content(sections)
    } catch (_: TimeoutCancellationException) {
        LoadState.Error("El servicio tardó demasiado en responder. Pulsa Reintentar.")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LoadState.Error("No se pudo cargar el servicio de música. ${error.message.orEmpty()}")
    }
}

internal fun MutableStateFlow<LoadState<List<CatalogSection>>>.purgePlaylist(playlistId: String) {
    val key = dev.sfg.numa.mobile.library.libraryPlaylistId(playlistId)
    val content = (value as? LoadState.Content) ?: return
    val updated = content.value.map { section ->
        section.copy(items = section.items.filterNot { item ->
            (item is dev.sfg.numa.mobile.model.CatalogItem.Collection && dev.sfg.numa.mobile.library.libraryPlaylistId(item.playlist.id) == key) ||
                dev.sfg.numa.mobile.library.libraryPlaylistId(item.stableId) == key
        })
    }
    value = LoadState.Content(updated)
}
