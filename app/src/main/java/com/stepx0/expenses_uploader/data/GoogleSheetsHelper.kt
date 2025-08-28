package com.stepx0.expenses_uploader.data

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.CellData
import com.google.api.services.sheets.v4.model.Spreadsheet
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
 * @param cellRange The cell containing the dropdown (e.g., "Sheet1!C2")
 * @return List of string options for the dropdown
 */
suspend fun fetchDropdownOptions(
    sheetsService: Sheets,
    spreadsheetId: String,
    cellRange: String
): List<String> = withContext(Dispatchers.IO) {
    val response: Spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
        .setRanges(listOf(cellRange))
        .setFields("sheets.data.rowData.values.dataValidation")
        .execute()

    //val validation = response.sheets[0].data[0].rowData[0].values[0].dataValidation
    val validation = (response.sheets[0].data[0].rowData[0].values.toList()[0] as CellData).dataValidation

    val condition = validation?.condition ?: return@withContext emptyList<String>()

    return@withContext when (condition.type) {
        "ONE_OF_LIST" -> {
            condition.values?.mapNotNull { (it as CellData).userEnteredValue.toString()
            }  // safely get userEnteredValue
                ?: emptyList()
        }

        "ONE_OF_RANGE" -> {
            val range = (condition.values?.firstOrNull() as CellData)?.userEnteredValue?.toString()
                ?: return@withContext emptyList()
            val result = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
            result.getValues()?.map { it[0].toString() } ?: emptyList()
        }

        else -> emptyList()
    }

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
        .append(spreadsheetId, "Sheet1!A:F", body)
        .setValueInputOption("RAW")
        .execute()
}
