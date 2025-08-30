package com.stepx0.expenses_uploader.model

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * BertModel for processing receipts.
 * Automatically adapts to the input/output tensor shapes of the TFLite model.
 */
class BertModel(private val context: Context) {

    private val tflite: Interpreter
    private val maxSequenceLength: Int

    init {
        // Load TFLite model
        val modelFile = FileUtil.loadMappedFile(context, "mobilebert-tflite-default-v1.tflite")
        tflite = Interpreter(modelFile)

        // Dynamically determine max sequence length from the input tensor shape
        val inputTensor = tflite.getInputTensor(0)
        val shape = inputTensor.shape() // [1, seq_len]
        maxSequenceLength = shape[1]

        printModelInfo()
    }

    private fun printModelInfo() {
        println("=== BERT Model Info ===")
        println("Input tensor count: ${tflite.inputTensorCount}")
        println("Output tensor count: ${tflite.outputTensorCount}")

        for (i in 0 until tflite.inputTensorCount) {
            val tensor = tflite.getInputTensor(i)
            println("Input $i: ${tensor.name()}, shape: ${tensor.shape().contentToString()}, type: ${tensor.dataType()}")
        }

        for (i in 0 until tflite.outputTensorCount) {
            val tensor = tflite.getOutputTensor(i)
            println("Output $i: ${tensor.name()}, shape: ${tensor.shape().contentToString()}, type: ${tensor.dataType()}")
        }
    }

    /**
     * Character-level tokenization
     * Converts OCR text to numeric token array
     */
    fun tokenizeOcrText(text: String): IntArray {
        val normalizedText = text.lowercase()
            .replace(Regex("[^a-z0-9\\s.,€$]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxSequenceLength)

        val tokenized = IntArray(maxSequenceLength) { 0 }
        for (i in normalizedText.indices) {
            val char = normalizedText[i]
            tokenized[i] = when {
                char.isDigit() -> char.digitToInt() + 10
                char.isLetter() -> (char - 'a') + 36
                char == ' ' -> 1
                char == '.' -> 2
                char == ',' -> 3
                char == '€' -> 4
                char == '$' -> 5
                else -> 0
            }
        }
        return tokenized
    }

    /**
     * Run BERT inference on OCR text
     */
    fun processReceiptText(ocrText: String): BertReceiptOutput {
        val tokenizedText = tokenizeOcrText(ocrText)

        // Prepare input buffers
        val inputBuffer = ByteBuffer.allocateDirect(4 * maxSequenceLength).order(ByteOrder.nativeOrder())
        val attentionMaskBuffer = ByteBuffer.allocateDirect(4 * maxSequenceLength).order(ByteOrder.nativeOrder())

        for (i in 0 until maxSequenceLength) {
            inputBuffer.putInt(if (i < tokenizedText.size) tokenizedText[i] else 0)
            attentionMaskBuffer.putInt(if (i < tokenizedText.size) 1 else 0)
        }
        inputBuffer.rewind()
        attentionMaskBuffer.rewind()

        // Determine output tensor shapes dynamically
        val outputDescShape = tflite.getOutputTensor(0).shape() // [1, seq_len] or similar
        val outputAmountShape = tflite.getOutputTensor(1).shape()

        val outputDescription = Array(outputDescShape[0]) { FloatArray(outputDescShape[1]) }
        val outputAmount = Array(outputAmountShape[0]) { FloatArray(outputAmountShape[1]) }

        val inputs = arrayOf(inputBuffer, attentionMaskBuffer)
        val outputs = mapOf(0 to outputDescription, 1 to outputAmount)

        // Run inference
        tflite.runForMultipleInputsOutputs(inputs, outputs)

        return BertReceiptOutput(
            originalText = ocrText,
            descriptionScores = outputDescription[0],
            amountScores = outputAmount[0],
            tokenizedInput = tokenizedText
        )
    }

    /**
     * Extract description using simple max-score sliding window
     */
    fun extractDescription(output: BertReceiptOutput): String {
        val text = output.originalText
        val scores = output.descriptionScores

        val window = 20
        var bestScore = 0f
        var bestStart = 0
        for (i in 0 until min(scores.size - window, text.length - window)) {
            val score = scores.sliceArray(i until i + window).average().toFloat()
            if (score > bestScore) {
                bestScore = score
                bestStart = i
            }
        }
        val end = min(bestStart + window, text.length)
        val extracted = text.substring(bestStart, end).trim()
        return extracted.ifEmpty { "Receipt Item" }
    }

    /**
     * Extract amount using highest confidence
     */
    fun extractAmount(output: BertReceiptOutput): String {
        val text = output.originalText
        val scores = output.amountScores

        val amountRegex = Regex("\\d+[.,]\\d{1,2}")
        val amounts = amountRegex.findAll(text).map { m ->
            val pos = m.range.first
            val conf = if (pos < scores.size) scores.sliceArray(pos until min(pos + m.value.length, scores.size)).average() else 0.0
            Triple(m.value.replace(",", "."), conf, m.value)
        }.toList()

        if (amounts.isNotEmpty()) {
            val best = amounts.maxByOrNull { it.second }
            return best?.first ?: ""
        }

        return ""
    }

    fun close() {
        tflite.close()
    }
}

/**
 * Holds BERT output for a receipt
 */
data class BertReceiptOutput(
    val originalText: String,
    val descriptionScores: FloatArray,
    val amountScores: FloatArray,
    val tokenizedInput: IntArray
)
