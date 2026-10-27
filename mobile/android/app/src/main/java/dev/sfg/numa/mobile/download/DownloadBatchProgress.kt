package dev.sfg.numa.mobile.download

data class DownloadBatchProgress(val completed: Int = 0, val total: Int = 0, val failed: Int = 0, val pending: Int = 0) {
    val active: Boolean get() = pending > 0
}

internal fun downloadBatchProgress(ids: Set<String>, downloads: Map<String, DownloadItem>): DownloadBatchProgress {
    val items = ids.mapNotNull(downloads::get)
    return DownloadBatchProgress(
        completed = items.count { it.status == DownloadStatus.COMPLETED },
        total = items.size,
        failed = items.count { it.status == DownloadStatus.FAILED },
        pending = items.count { it.isDownloading },
    )
}
