package com.example.lyriccaptioner.processing.enhancement

object CaptionLocalRepairContract {
    const val SCHEMA_VERSION = "caption-local-repair.v1"
}

data class CaptionLocalRepairRequest(
    val jobId: String,
    val cues: List<CaptionLocalRepairRequestCue>,
)

data class CaptionLocalRepairRequestCue(
    val id: String,
    val parentSourceId: String,
    val siblingId: String,
    val parentRawEnglish: String,
    val english: String,
    val chinese: String,
    val previousEnglish: String?,
    val nextEnglish: String?,
    val canonicalEnglish: String?,
)

data class CaptionLocalRepairResponse(
    val schemaVersion: String,
    val jobId: String,
    val processingVersion: String,
    val cues: List<CaptionLocalRepairResponseCue>,
)

data class CaptionLocalRepairResponseCue(
    val id: String,
    val correctedEnglish: String,
    val chinese: String,
)

internal object CaptionLocalRepairBatchPolicy {
    fun build(
        request: CaptionEnhancementRequest,
        firstPass: CaptionEnhancementResponse,
        verified: VerifiedSongLyrics?,
    ): CaptionLocalRepairRequest? {
        val parentById = request.cues.associateBy { it.id }
        val allLines = firstPass.cues.flatMap { cue ->
            cue.lines.mapIndexed { index, line ->
                val id = childId(cue.sourceId, cue.lines.size, index)
                FlatLine(id, cue.sourceId, line)
            }
        }
        val splitParentIds = firstPass.cues.filter { it.lines.size == 2 }.mapTo(hashSetOf()) { it.sourceId }
        if (splitParentIds.isEmpty()) return null

        val repairCues = allLines.mapIndexedNotNull { index, flat ->
            if (flat.parentSourceId !in splitParentIds) return@mapIndexedNotNull null
            val siblings = allLines.filter { it.parentSourceId == flat.parentSourceId }
            val sibling = siblings.single { it.id != flat.id }
            val canonicalLines = verified?.cueCanonicalLines?.get(flat.parentSourceId)
            val lineIndex = siblings.indexOfFirst { it.id == flat.id }
            CaptionLocalRepairRequestCue(
                id = flat.id,
                parentSourceId = flat.parentSourceId,
                siblingId = sibling.id,
                parentRawEnglish = parentById.getValue(flat.parentSourceId).rawEnglish,
                english = flat.line.correctedEnglish,
                chinese = flat.line.chinese,
                previousEnglish = allLines.getOrNull(index - 1)?.line?.correctedEnglish,
                nextEnglish = allLines.getOrNull(index + 1)?.line?.correctedEnglish,
                canonicalEnglish = canonicalLines?.getOrNull(lineIndex),
            )
        }
        return CaptionLocalRepairRequest(
            jobId = "${request.jobId}:repair",
            cues = repairCues,
        )
    }

    fun apply(
        request: CaptionLocalRepairRequest,
        response: CaptionLocalRepairResponse,
        firstPass: CaptionEnhancementResponse,
    ): CaptionEnhancementResponse {
        if (response.schemaVersion != CaptionLocalRepairContract.SCHEMA_VERSION) reject()
        if (response.jobId != request.jobId) reject()
        requireText(response.processingVersion, allowBlank = false, "repair processing version")
        val expectedIds = request.cues.map { it.id }
        val actualIds = response.cues.map { it.id }
        if (actualIds != expectedIds || actualIds.toSet().size != actualIds.size) reject()

        val repairedById = response.cues.mapIndexed { index, result ->
            requireIdentifier(result.id, "repair cue id")
            requireText(result.correctedEnglish, allowBlank = false, "repaired English")
            requireText(result.chinese, allowBlank = false, "repaired Chinese")
            val expected = request.cues[index]
            expected.canonicalEnglish?.let { canonical ->
                if (normalize(result.correctedEnglish) != normalize(canonical)) reject()
            }
            result.id to result
        }.toMap()

        val repairedParents = firstPass.cues.map { parent ->
            if (parent.lines.size != 2) return@map parent
            parent.copy(
                lines = parent.lines.mapIndexed { index, original ->
                    val id = childId(parent.sourceId, parent.lines.size, index)
                    val repaired = repairedById[id] ?: reject()
                    val canonical = request.cues.first { it.id == id }.canonicalEnglish
                    original.copy(
                        correctedEnglish = canonical ?: repaired.correctedEnglish.trim(),
                        chinese = repaired.chinese.trim(),
                    )
                },
            )
        }
        return firstPass.copy(
            processingVersion = response.processingVersion,
            cues = repairedParents,
            processingLevel = CaptionProcessingLevel.TWO_PASS_COMPLETE,
        )
    }

    private fun childId(parentId: String, lineCount: Int, index: Int): String =
        if (lineCount == 1) parentId else "$parentId:${index + 1}"

    private fun normalize(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun reject(): Nothing = throw CaptionEnhancementProviderException(
        kind = CaptionEnhancementErrorKind.INVALID_RESPONSE,
        safeDetail = "Caption local repair response was invalid.",
    )

    private data class FlatLine(
        val id: String,
        val parentSourceId: String,
        val line: CaptionEnhancementResponseLine,
    )
}
