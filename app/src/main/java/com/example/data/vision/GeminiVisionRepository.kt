package com.example.data.vision

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiVisionAnalysis
import com.example.data.model.ImageFingerprint
import com.example.data.model.InventoryItem
import com.example.data.model.ScanMode
import com.example.data.model.SimilarCandidate
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiVisionRepo"
private const val VISION_MODEL = "gemini-3.8-flash"

/**
 * Performs micro-OCR, embossed/debossed relief inspection, and inventory variant matching
 * using Firebase AI Logic (Gemini 3.8 Flash) with strict JSON responseSchema output.
 */
class GeminiVisionRepository {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "suggestedName" to Schema.string(
                description = "Concise, distinctive variant product name (e.g., '22mm Brass Coin — Series A')."
            ),
            "extractedText" to Schema.string(
                description = "Exact embossed, debossed, stamped, printed, or engraved characters/numbers visible on the object. Empty string if none."
            ),
            "designDetails" to Schema.string(
                description = "Specific visual relief, finish, material, pattern, border, or silhouette details that distinguish this variant."
            ),
            "category" to Schema.string(
                description = "Short inventory category (e.g., Hardware, Packaging, Accessories, Cosmetics, Parts, General)."
            ),
            "suggestedThreshold" to Schema.integer(
                description = "Recommended low-stock alert threshold integer between 1 and 50."
            ),
            "confidence" to Schema.double(
                description = "Match confidence between 0.0 and 1.0."
            ),
            "confidenceNote" to Schema.string(
                description = "Short 1-sentence explanation of why this matches an existing SKU or why confirmation is needed."
            ),
            "exactMatchId" to Schema.string(
                description = "Document ID of the exact existing catalog SKU ONLY if text, relief, and variant match with >=0.88 certainty; otherwise empty string.",
                nullable = true
            ),
            "isAmbiguous" to Schema.boolean(
                description = "True when the item resembles one or more existing SKUs but differs in embossed text, relief, shade, or certainty is below 0.88."
            ),
            "similarCandidates" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "Existing catalog item ID"),
                        "similarity" to Schema.double(description = "Estimated similarity 0.0 to 1.0"),
                        "reason" to Schema.string(description = "Why this candidate is visually similar or differs")
                    )
                ),
                description = "Up to 4 similar existing catalog items for user confirmation."
            )
        ),
        optionalProperties = listOf("exactMatchId")
    )

    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = VISION_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
                temperature = 0.15f
            }
        )
    }

    suspend fun analyzeProductPhoto(
        bitmap: Bitmap,
        fingerprint: ImageFingerprint?,
        existingItems: List<InventoryItem>,
        mode: ScanMode
    ): Result<AiVisionAnalysis> {
        val localCandidates = ImageProcessor.findLocalCandidates(fingerprint, existingItems, minScore = 0.68)

        val catalogSummary = if (existingItems.isEmpty()) {
            "Catalog is currently empty."
        } else {
            existingItems.take(45).joinToString("\n") { item ->
                "- id=\"${item.id}\" | name=\"${item.name}\" | embossedText=\"${item.extractedText}\" | design=\"${item.designDetails}\" | category=\"${item.category}\" | qty=${item.quantity}"
            }
        }

        val prompt = buildString {
            appendLine("You are AURELIA STOCK Vision AI, a high-precision micro-OCR and industrial SKU variant recognition specialist.")
            appendLine("Operation mode: Stock ${mode.name}.")
            appendLine()
            appendLine("CRITICAL INSPECTION RULES:")
            appendLine("1. Read ALL embossed, debossed, stamped, molded, engraved, or printed text, numbers, serials, sizes, or logos on the item carefully.")
            appendLine("2. Describe surface design/relief details (color, metal/plastic finish, knurling, borders, emblem, shape) in designDetails.")
            appendLine("3. Compare carefully against the EXISTING CATALOG below.")
            appendLine("4. NEVER blindly merge two distinct variants! If embossed/printed text differs OR surface relief/color differs even slightly, set exactMatchId to \"\" and set isAmbiguous=true, listing the close SKU(s) in similarCandidates so the operator confirms.")
            appendLine("5. Only populate exactMatchId when the photographed product is unequivocally the EXACT same SKU variant (confidence >= 0.88 and consistent text/relief).")
            appendLine("6. Keep suggestedName under 60 characters, clean, and specific to the variant.")
            appendLine()
            appendLine("EXISTING CATALOG:")
            appendLine(catalogSummary)
        }

        return try {
            val response = withTimeout(60_000L) {
                visionModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini Vision returned an empty response."))

            val parsed = parseAndVerifyAnalysis(rawJson, existingItems, localCandidates)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Vision timed out after 60s", e)
            Result.failure(
                IllegalStateException("Vision AI analysis timed out after 60 seconds. You can retry or complete the fields manually.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Vision analysis failed", e)
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Vision AI analysis failed.")
            )
        }
    }

    private fun parseAndVerifyAnalysis(
        rawJson: String,
        existingItems: List<InventoryItem>,
        localCandidates: List<SimilarCandidate>
    ): AiVisionAnalysis {
        val json = JSONObject(rawJson)
        val itemMap = existingItems.associateBy { it.id }

        val suggestedName = json.optString("suggestedName", "").trim().ifBlank { "New Scanned Variant" }
        val extractedText = json.optString("extractedText", "").trim().take(200)
        val designDetails = json.optString("designDetails", "").trim().take(300)
        val category = json.optString("category", "").trim().ifBlank { "General" }.take(60)
        val suggestedThreshold = json.optInt("suggestedThreshold", 5).coerceIn(1, 500)
        val confidence = json.optDouble("confidence", 0.72).coerceIn(0.0, 1.0)
        var confidenceNote = json.optString("confidenceNote", "").trim()
        var exactMatchId = json.optString("exactMatchId", "").trim().takeIf { it.isNotEmpty() && itemMap.containsKey(it) }
        var ambiguous = json.optBoolean("isAmbiguous", false)

        val candidatesList = mutableListOf<SimilarCandidate>()
        val candidatesJson = json.optJSONArray("similarCandidates")
        if (candidatesJson != null) {
            for (i in 0 until candidatesJson.length()) {
                val obj = candidatesJson.optJSONObject(i) ?: continue
                val cid = obj.optString("id", "").trim()
                if (cid.isNotEmpty() && itemMap.containsKey(cid)) {
                    candidatesList.add(
                        SimilarCandidate(
                            id = cid,
                            similarity = obj.optDouble("similarity", 0.75).coerceIn(0.0, 1.0),
                            reason = obj.optString("reason", "Similar visual profile").trim()
                        )
                    )
                }
            }
        }

        // Supplement with local perceptual fingerprint candidates if not already listed
        for (local in localCandidates) {
            if (candidatesList.none { it.id == local.id }) {
                candidatesList.add(local)
            }
        }

        // Safety Guard: Embossed OCR text conflict protection (prevent blind variant merge)
        if (exactMatchId != null) {
            val matchedItem = itemMap[exactMatchId]
            if (matchedItem != null) {
                val newNorm = ImageProcessor.normalizeOcrText(extractedText)
                val existingNorm = ImageProcessor.normalizeOcrText(matchedItem.extractedText)
                val textMismatch = newNorm.isNotEmpty() &&
                    existingNorm.isNotEmpty() &&
                    newNorm != existingNorm &&
                    !newNorm.contains(existingNorm) &&
                    !existingNorm.contains(newNorm)

                if (textMismatch || confidence < 0.85) {
                    val demotedReason = if (textMismatch) {
                        "Embossed text differs (\"$extractedText\" vs \"${matchedItem.extractedText}\") — confirm before merging"
                    } else {
                        "Confidence ${(confidence * 100).toInt()}% — verify variant match"
                    }
                    if (candidatesList.none { it.id == matchedItem.id }) {
                        candidatesList.add(0, SimilarCandidate(matchedItem.id, confidence, demotedReason))
                    }
                    exactMatchId = null
                    ambiguous = true
                    confidenceNote = demotedReason
                }
            }
        }

        // If no exact match but strong local/AI candidates exist, prompt confirmation
        if (exactMatchId == null && candidatesList.any { it.similarity >= 0.72 }) {
            ambiguous = true
            if (confidenceNote.isBlank()) {
                confidenceNote = "Found similar saved variant(s). Confirm whether to update or create a separate SKU."
            }
        }

        return AiVisionAnalysis(
            suggestedName = suggestedName.take(120),
            extractedText = extractedText,
            designDetails = designDetails,
            category = category,
            suggestedThreshold = suggestedThreshold,
            confidence = confidence,
            confidenceNote = confidenceNote.ifBlank {
                if (exactMatchId != null) "Exact SKU variant recognized." else "New variant profile detected."
            },
            exactMatchId = exactMatchId,
            ambiguous = ambiguous,
            similarCandidates = candidatesList.distinctBy { it.id }.sortedByDescending { it.similarity }.take(4)
        )
    }
}
