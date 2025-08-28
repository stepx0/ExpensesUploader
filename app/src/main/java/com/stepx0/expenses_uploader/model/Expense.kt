package com.stepx0.expenses_uploader.model

/**
 * Represents a single expense entry.
 */
data class Expense(
    val year: String,
    val month: String,
    val day: String,
    val description: String,
    val amount: String,
    val currency: String,
    val category: String,
    val method: String
) {
    /**
     * Convert this expense into a row (list of strings)
     * ready to be sent to Google Sheets.
     */
    fun toRow(): List<String> {
        val date = "$year-$month-$day"
        return listOf(
            date,
            description,
            amount,
            currency,
            category,
            method
        )
    }
}
