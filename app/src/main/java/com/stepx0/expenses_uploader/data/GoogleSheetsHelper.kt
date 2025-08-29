package com.stepx0.expenses_uploader.data

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.stepx0.expenses_uploader.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetch the dropdown options for a specific cell.
 * Supports both ONE_OF_LIST and ONE_OF_RANGE validation types.
 *
 * @param sheetsService Authenticated Sheets API client
 * @param spreadsheetId The Google Sheet ID
 * @param sheetName The sheet name
 * @return List of string options for the dropdown
 */
suspend fun fetchCategoryValues(
    sheetsService: Sheets,
    spreadsheetId: String,
    sheetName: String,
    columnRange: String
): List<String> = withContext(Dispatchers.IO) {
    val response = sheetsService.spreadsheets().values()
        .get(spreadsheetId, "$sheetName!$columnRange")
        .execute()

    val values = response.getValues() ?: emptyList<List<Any>>()
    values.mapNotNull { it.getOrNull(0)?.toString() }.filter { it.isNotEmpty() }
}


/**
 * Append a single expense row to the Google Sheet.
 *
 * @param sheetsService Authenticated Sheets API client
 * @param spreadsheetId The Google Sheet ID
 * @param expense Expense object to append
 */
suspend fun appendExpenseRow(
    sheetsService: Sheets,
    spreadsheetId: String,
    expense: Expense
) = withContext(Dispatchers.IO) {
    val body = ValueRange().setValues(listOf(expense.toRow()))
    sheetsService.spreadsheets().values()
        .append(spreadsheetId, "Expenses!A:I", body)
        .setValueInputOption("RAW")
        .setIncludeValuesInResponse(false)
        .execute()
}
