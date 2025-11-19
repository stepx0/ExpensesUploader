package com.stepx0.expenses_uploader.model

import android.util.Log

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
    val subCategory: String
) {
    /**
     * Convert this expense into a row (list of strings)
     * ready to be sent to Google Sheets.
     */
    fun toRow(): List<*> {
        return listOf(
            month.toDouble(),
            day.toDouble(),
            description,
            amount.toDouble(),
            currency,
            "", // % company (in case it's for a business)
            amount.toDouble(),
            category,
            subCategory
        )
    }

    fun print() {
        Log.d("Expense -- ",
            "month: "+ month + ", " +
                    "day: " + day + ", " +
                    "description: " + description + ", " +
                    "amount: " + amount + ", " +
                    "currency: " + currency)
    }
}
