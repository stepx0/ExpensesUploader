package com.stepx0.expenses_uploader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.stepx0.expenses_uploader.ui.ExpenseForm
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.stepx0.expenses_uploader.ui.theme.ExpensesUploaderTheme

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val spreadsheetId = "YOUR_SPREADSHEET_ID"

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    // Initialize Sheets service for this account
                    val sheetsService = initSheetsService(account)
                    // Update Compose state
                    accountState = account
                    this.sheetsService = sheetsService
                }
            } catch (e: ApiException) {
                Log.e("MainActivity", "Google Sign-In failed: ${e.statusCode}")
            }
        }

    // Compose-observed states
    private var accountState by mutableStateOf<GoogleSignInAccount?>(null)
    private var sheetsService: Sheets? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/spreadsheets"))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Check if already signed in
        accountState = GoogleSignIn.getLastSignedInAccount(this)
        if (accountState != null) {
            sheetsService = initSheetsService(accountState!!)
        }

        setContent {
            ExpensesUploaderTheme {
                ProvideWindowInsets {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (accountState == null) {
                            Button(onClick = { signInLauncher.launch(googleSignInClient.signInIntent) }) {
                                Text("Login with Google")
                            }
                        } else {
                            Text("Signed in as: ${accountState?.email}")
                            Button(onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    sheetsService = null
                                    accountState = null
                                }
                            }) {
                                Text("Logout")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ExpenseForm(
                                sheetsService = sheetsService,
                                spreadsheetId = spreadsheetId,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initSheetsService(account: GoogleSignInAccount): Sheets {
        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf("https://www.googleapis.com/auth/spreadsheets")
        )
        credential.selectedAccount = account.account

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ExpensesUploader")
            .build()
    }
}
