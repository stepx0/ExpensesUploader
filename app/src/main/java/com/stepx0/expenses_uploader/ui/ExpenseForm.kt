package com.stepx0.expenses_uploader.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    val scope = rememberCoroutineScope()

    // Load dropdown options
    LaunchedEffect(sheetsService) {
        if (sheetsService != null && spreadsheetId.isNotEmpty()) {
            isLoading = true
            primaryCategoryOptions = fetchCategoryValues(sheetsService, spreadsheetId, "Expense Validation", columnRange = "E2:E")
            secondaryCategoryOptions = fetchCategoryValues(sheetsService, spreadsheetId, "Expense Validation", columnRange = "F2:F")
            isLoading = false
        }
    }

    // Enable scrolling
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // ✅ makes whole form scrollable
            .padding(16.dp),
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
                if (sheetsService != null && spreadsheetId.isNotEmpty()) {
                    val expense = Expense(
                        year = year,
                        month = month,
                        day = day,
                        description = description,
                        amount = amount,
                        currency = "", // currency omitted
                        category = selectedPrimaryCategory,
                        subCategory = selectedSecondaryCategory
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

        // Open Sheet button
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
