package com.stepx0.expenses_uploader.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.api.services.sheets.v4.Sheets
import com.stepx0.expenses_uploader.data.appendExpenseRow
import com.stepx0.expenses_uploader.data.fetchCategoryValues
import com.stepx0.expenses_uploader.model.Expense
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseForm(
    modifier: Modifier = Modifier,
    sheetsService: Sheets?,
    spreadsheetId: String,
    gid: String? = null
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val focusManager = LocalFocusManager.current // Focus manager

    // Date state
    var year by remember { mutableStateOf(calendar.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((calendar.get(Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH).toString()) }

    // Other fields
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    // Dropdowns
    var primaryCategoryExpanded by remember { mutableStateOf(false) }
    var selectedPrimaryCategory by remember { mutableStateOf("") }
    var primaryCategoryOptions by remember { mutableStateOf(listOf<String>()) }

    var secondaryCategoryExpanded by remember { mutableStateOf(false) }
    var selectedSecondaryCategory by remember { mutableStateOf("") }
    var secondaryCategoryOptions by remember { mutableStateOf(listOf<String>()) }

    var isLoading by remember { mutableStateOf(true) }
    val isSaveEnabled = description.isNotBlank() && amount.isNotBlank()

    val scope = rememberCoroutineScope()

    // Load dropdown options
    LaunchedEffect(sheetsService) {
        if (sheetsService != null && spreadsheetId.isNotEmpty()) {
            isLoading = true
            try {
                primaryCategoryOptions = fetchCategoryValues(
                    sheetsService,
                    spreadsheetId,
                    "Expense Validation",
                    columnRange = "E2:E"
                )
                secondaryCategoryOptions = fetchCategoryValues(
                    sheetsService,
                    spreadsheetId,
                    "Expense Validation",
                    columnRange = "F2:F"
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load categories: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    // Enable scrolling and tap outside to clear focus
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .pointerInput(Unit) { // Detect taps outside TextFields
                detectTapGestures { focusManager.clearFocus() }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Default currency: €")

        // 📅 Date Picker
        Button(
            onClick = {
                focusManager.clearFocus() // Clear focus when opening DatePicker
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { amount = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth()
        )

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Primary Category dropdown
            ExposedDropdownMenuBox(
                expanded = primaryCategoryExpanded,
                onExpandedChange = { primaryCategoryExpanded = !primaryCategoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedPrimaryCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Primary Category") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = primaryCategoryExpanded,
                    onDismissRequest = { primaryCategoryExpanded = false }
                ) {
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

            // Secondary Category dropdown
            ExposedDropdownMenuBox(
                expanded = secondaryCategoryExpanded,
                onExpandedChange = { secondaryCategoryExpanded = !secondaryCategoryExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSecondaryCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Secondary Category") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = secondaryCategoryExpanded,
                    onDismissRequest = { secondaryCategoryExpanded = false }
                ) {
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
                focusManager.clearFocus()
                if (sheetsService != null && spreadsheetId.isNotEmpty()) {
                    val expense = Expense(
                        year = year,
                        month = month,
                        day = day,
                        description = description,
                        amount = amount,
                        currency = "",
                        category = selectedPrimaryCategory,
                        subCategory = selectedSecondaryCategory
                    )
                    scope.launch {
                        try {
                            appendExpenseRow(sheetsService, spreadsheetId, expense)
                            // Clear form on success
                            year = calendar.get(Calendar.YEAR).toString()
                            month = (calendar.get(Calendar.MONTH) + 1).toString()
                            day = calendar.get(Calendar.DAY_OF_MONTH).toString()
                            description = ""
                            amount = ""
                            selectedPrimaryCategory = ""
                            selectedSecondaryCategory = ""
                            Toast.makeText(context, "Expense added successfully!", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error! Failed to upload expense.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Sheets service unavailable.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isSaveEnabled // disable button if amount or description is empty
        ) {
            Text("Save Expense")
        }

        // Open Sheet link
        Text(
            text = "Open Google Sheet",
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable {
                    val url =
                        "https://docs.google.com/spreadsheets/d/$spreadsheetId" +
                                if (!gid.isNullOrBlank())
                                    "/edit?usp=drivesdk&gid=$gid"
                                else ""
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                }
        )
    }
}
