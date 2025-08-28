package com.stepx0.expenses_uploader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.api.services.sheets.v4.Sheets
import com.stepx0.expenses_uploader.data.appendExpenseRow
import com.stepx0.expenses_uploader.data.fetchDropdownOptions
import com.stepx0.expenses_uploader.model.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseForm(
    modifier: Modifier = Modifier,
    sheetsService: Sheets?,
    spreadsheetId: String
) {
    // State for form fields
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }

    // Dropdown: Category
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var categoryOptions by remember { mutableStateOf(listOf<String>()) }

    // Dropdown: Method
    var methodExpanded by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf("") }
    var methodOptions by remember { mutableStateOf(listOf<String>()) }

    // Coroutine scope
    val scope = rememberCoroutineScope()

    // Load dropdown options from Sheets
    LaunchedEffect(sheetsService) {
        if (sheetsService != null && spreadsheetId.isNotEmpty()) {
            categoryOptions = fetchDropdownOptions(sheetsService, spreadsheetId, "Sheet1!C2")
            methodOptions = fetchDropdownOptions(sheetsService, spreadsheetId, "Sheet1!D2")
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date inputs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = month,
                onValueChange = { month = it },
                label = { Text("Month") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = day,
                onValueChange = { day = it },
                label = { Text("Day") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Year") },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text("Currency") },
            modifier = Modifier.fillMaxWidth()
        )

        // Category dropdown
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categoryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedCategory = option
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // Method dropdown
        ExposedDropdownMenuBox(
            expanded = methodExpanded,
            onExpandedChange = { methodExpanded = !methodExpanded }
        ) {
            OutlinedTextField(
                value = selectedMethod,
                onValueChange = {},
                readOnly = true,
                label = { Text("Payment Method") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = methodExpanded,
                onDismissRequest = { methodExpanded = false }
            ) {
                methodOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedMethod = option
                            methodExpanded = false
                        }
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                if (sheetsService != null && spreadsheetId.isNotEmpty()) {
                    val expense = Expense(
                        year = year,
                        month = month,
                        day = day,
                        description = description,
                        amount = amount,
                        currency = currency,
                        category = selectedCategory,
                        method = selectedMethod
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
