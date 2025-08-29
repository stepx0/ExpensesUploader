package com.stepx0.expenses_uploader.ui

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.api.services.sheets.v4.Sheets
import com.stepx0.expenses_uploader.data.appendExpenseRow
import com.stepx0.expenses_uploader.data.fetchCategoryValues
import com.stepx0.expenses_uploader.data.fetchDropdownOptions
import com.stepx0.expenses_uploader.model.Expense
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseForm(
    modifier: Modifier = Modifier,
    sheetsService: Sheets?,
    spreadsheetId: String
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Date state
    var year by remember { mutableStateOf(calendar.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((calendar.get(Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH).toString()) }

    // Other fields
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    // Dropdowns: primary + secondary categories
    var primaryCategoryExpanded by remember { mutableStateOf(false) }
    var selectedPrimaryCategory by remember { mutableStateOf("") }
    var primaryCategoryOptions by remember { mutableStateOf(listOf<String>()) }

    var secondaryCategoryExpanded by remember { mutableStateOf(false) }
    var selectedSecondaryCategory by remember { mutableStateOf("") }
    var secondaryCategoryOptions by remember { mutableStateOf(listOf<String>()) }

    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    // Load dropdown options from Sheets
    LaunchedEffect(sheetsService) {
        if (sheetsService != null && spreadsheetId.isNotEmpty()) {
            isLoading = true
            primaryCategoryOptions = fetchCategoryValues(sheetsService, spreadsheetId, "2025 Expenses")
            secondaryCategoryOptions = fetchCategoryValues(sheetsService, spreadsheetId, "2025 Expenses")
            isLoading = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Default currency: €")

        // 📅 Date Picker
        Button(
            onClick = {
                DatePickerDialog(
                    context,
                    { _: DatePicker, y: Int, m: Int, d: Int ->
                        year = y.toString()
                        month = (m + 1).toString()
                        day = d.toString()
                    },
                    year.toInt(),
                    month.toInt() - 1,
                    day.toInt()
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$day/$month/$year")
        }

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        // Primary Category dropdown
        ExposedDropdownMenuBox(
            expanded = primaryCategoryExpanded,
            onExpandedChange = { if (!isLoading) primaryCategoryExpanded = !primaryCategoryExpanded }
        ) {
            OutlinedTextField(
                value = if (selectedPrimaryCategory.isEmpty() && isLoading) "Loading..." else selectedPrimaryCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Primary Category") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = !isLoading
            )
            ExposedDropdownMenu(
                expanded = primaryCategoryExpanded,
                onDismissRequest = { primaryCategoryExpanded = false }
            ) {
                if (isLoading) {
                    DropdownMenuItem(
                        text = { Text("Loading options...") },
                        onClick = {}
                    )
                } else {
                    primaryCategoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedPrimaryCategory = option
                                primaryCategoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Secondary Category dropdown
        ExposedDropdownMenuBox(
            expanded = secondaryCategoryExpanded,
            onExpandedChange = { if (!isLoading) secondaryCategoryExpanded = !secondaryCategoryExpanded }
        ) {
            OutlinedTextField(
                value = if (selectedSecondaryCategory.isEmpty() && isLoading) "Loading..." else selectedSecondaryCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Secondary Category") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = !isLoading
            )
            ExposedDropdownMenu(
                expanded = secondaryCategoryExpanded,
                onDismissRequest = { secondaryCategoryExpanded = false }
            ) {
                if (isLoading) {
                    DropdownMenuItem(
                        text = { Text("Loading options...") },
                        onClick = {}
                    )
                } else {
                    secondaryCategoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedSecondaryCategory = option
                                secondaryCategoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Save button
        Button(
            onClick = {
                if (sheetsService != null && spreadsheetId.isNotEmpty()) {
                    val expense = Expense(
                        year = year,
                        month = month,
                        day = day,
                        description = description,
                        amount = amount,
                        currency = "", // currency omitted
                        category = selectedPrimaryCategory,
                        method = selectedSecondaryCategory // using secondary category here
                    )
                    scope.launch {
                        appendExpenseRow(sheetsService, spreadsheetId, expense)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Expense")
        }
    }
}

