package com.stepx0.expenses_uploader.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.stepx0.expenses_uploader.model.BertModel
import kotlinx.coroutines.tasks.await

/**
 * Helper object for OCR with ML Kit and BERT model integration.
 * Pure BERT-based extraction without heuristic fallbacks.
 */
object OcrHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Run OCR on a [Bitmap] and return the recognized text.
     */
    suspend fun runOcrOnBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text
    }

    /**
     * Run OCR on an [Uri] (e.g. from gallery/camera).
     */
    suspend fun runOcrOnUri(context: Context, uri: Uri): String {
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        return runOcrOnBitmap(bitmap)
    }

    /**
     * Process receipt image using OCR + BERT model for intelligent extraction
     * This is the main method you should use - pure BERT-based extraction
     */
    suspend fun processReceiptWithBert(
        context: Context,
        ocrText: String,
        bertModel: BertModel
    ): Pair<String, String> {

        if (ocrText.isBlank()) {
            return "No text found in image" to ""
        }

        // Step 2: Process with BERT model
        val bertOutput = bertModel.processReceiptText(ocrText)

        // Step 3: Extract description and amount using BERT predictions
        val description = bertModel.extractDescription(bertOutput)
        val amount = bertModel.extractAmount(bertOutput)

        // Step 4: Clean and validate results
        val cleanDescription = cleanBertDescription(description)
        val cleanAmount = cleanBertAmount(amount)

        return cleanDescription to cleanAmount
    }

    /**
     * Alternative method for batch processing multiple images
     */
    suspend fun processBatchReceipts(
        context: Context,
        uris: List<Uri>,
        bertModel: BertModel,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<Triple<Uri, String, String>> {
        val results = mutableListOf<Triple<Uri, String, String>>()

        uris.forEachIndexed { index, uri ->
            try {
                val ocrText = runOcrOnUri(context, uri)

                val (description, amount) = processReceiptWithBert(context, ocrText, bertModel)
                results.add(Triple(uri, description, amount))
                onProgress?.invoke(index + 1, uris.size)
            } catch (e: Exception) {
                results.add(Triple(uri, "Error processing image", ""))
                onProgress?.invoke(index + 1, uris.size)
            }
        }

        return results
    }

    /**
     * Clean and validate BERT-extracted description
     */
    private fun cleanBertDescription(bertDescription: String): String {
        var cleaned = bertDescription.trim()

        // Remove common OCR artifacts and currency symbols
        cleaned = cleaned
            .replace(Regex("[*#@€$]+"), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\d.,]+$"), "") // Remove if it's just numbers
            .trim()

        // Validate minimum quality
        if (cleaned.length < 2 || cleaned.all { !it.isLetter() }) {
            return "Receipt Item"
        }

        // Capitalize first letter and limit length
        return cleaned.replaceFirstChar { it.uppercaseChar() }.take(50)
    }

    /**
     * Clean and validate BERT-extracted amount
     */
    private fun cleanBertAmount(bertAmount: String): String {
        if (bertAmount.isBlank()) return ""

        // Normalize decimal separator
        var cleaned = bertAmount.replace(",", ".").trim()

        // Extract valid monetary amount
        val amountRegex = Regex("\\d+\\.\\d{1,2}")
        val match = amountRegex.find(cleaned)

        if (match != null) {
            val value = match.value.toDoubleOrNull()
            if (value != null && value > 0 && value <= 99999) {
                // Format to 2 decimal places
                return "%.2f".format(value)
            }
        }

        // Try to parse as integer and convert to decimal
        val intRegex = Regex("\\d+")
        val intMatch = intRegex.find(cleaned)
        if (intMatch != null) {
            val intValue = intMatch.value.toDoubleOrNull()
            if (intValue != null && intValue > 0 && intValue <= 99999) {
                return "%.2f".format(intValue)
            }
        }

        return ""
    }

    /**
     * Get detailed extraction info for debugging
     */
    suspend fun getExtractionDebugInfo(
        context: Context,
        uri: Uri,
        bertModel: BertModel
    ): ReceiptExtractionDebugInfo {
        val ocrText = runOcrOnUri(context, uri)
        val bertOutput = bertModel.processReceiptText(ocrText)

        return ReceiptExtractionDebugInfo(
            originalOcrText = ocrText,
            tokenizedLength = bertOutput.tokenizedInput.size,
            descriptionConfidenceAvg = bertOutput.descriptionScores.average().toFloat(),
            amountConfidenceAvg = bertOutput.amountScores.average().toFloat(),
            extractedDescription = bertModel.extractDescription(bertOutput),
            extractedAmount = bertModel.extractAmount(bertOutput),
            maxDescriptionConfidence = bertOutput.descriptionScores.maxOrNull() ?: 0f,
            maxAmountConfidence = bertOutput.amountScores.maxOrNull() ?: 0f
        )
    }

    /**
     * heuristic to extract amount (typically more precise)
     */
    fun extractTotal(lines: List<String>): String {
        val keywords = listOf(
            "TOTALE COMPLESSIVO",
            "Pagamento",
            "TOTALE",
            "IMPORTO",
            "TOTALE(EUR)",
            "TOTALE PAGATO",
            "PAGATO",
            "PAGAMENTO",
            "IMPORTO TOTALE",
            "TOT."
        )

        val totalIndex = lines.indexOfFirst { line ->
            keywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
        }

        if (totalIndex != -1 && totalIndex + 1 < lines.size) {
            val candidate = lines[totalIndex + 1]
                .replace(",", ".")
                .filter { it.isDigit() || it == '.' }
            if (candidate.isNotEmpty()) return candidate
        }

        val numbers = lines.flatMap { line ->
            Regex("\\d+[.,]\\d{2}")
                .findAll(line)
                .map { it.value.replace(",", ".") }
        }

        return numbers.maxOfOrNull { it.toDoubleOrNull() ?: 0.0 }?.toString() ?: ""
    }

    /**
     * Check if an image likely contains a receipt
     */
    suspend fun isReceiptImage(context: Context, uri: Uri, bertModel: BertModel): Boolean {
        val ocrText = runOcrOnUri(context, uri)

        // Quick heuristic check for receipt-like content
        val receiptKeywords = listOf(
            "total", "totale", "receipt", "scontrino", "€", "$",
            "tax", "iva", "payment", "pagamento", "cash", "card"
        )

        val hasReceiptKeywords = receiptKeywords.any {
            ocrText.contains(it, ignoreCase = true)
        }

        val hasAmountPattern = Regex("\\d+[.,]\\d{2}").containsMatchIn(ocrText)

        return hasReceiptKeywords && hasAmountPattern
    }
}

/**
 * Data class for debugging extraction results
 */
data class ReceiptExtractionDebugInfo(
    val originalOcrText: String,
    val tokenizedLength: Int,
    val descriptionConfidenceAvg: Float,
    val amountConfidenceAvg: Float,
    val extractedDescription: String,
    val extractedAmount: String,
    val maxDescriptionConfidence: Float,
    val maxAmountConfidence: Float
) {
    fun isExtractionReliable(): Boolean {
        return maxDescriptionConfidence > 0.3f && maxAmountConfidence > 0.3f
    }
}