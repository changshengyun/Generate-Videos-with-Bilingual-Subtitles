package com.example.lyriccaptioner.processing

import android.net.Uri
import android.provider.DocumentsContract

object ExportDestinationPolicy {
    fun isSameDocument(source: Uri, destination: Uri): Boolean {
        if (source.toString() == destination.toString()) return true
        if (source.scheme != destination.scheme || source.authority != destination.authority) return false
        val sourceId = documentId(source) ?: return false
        val destinationId = documentId(destination) ?: return false
        return sourceId == destinationId
    }

    private fun documentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
    }.getOrNull()
}
