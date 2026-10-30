package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import com.example.data.model.*
import com.example.ui.screens.*
import com.example.ui.theme.PrimaryNavy
import com.example.ui.theme.SmartBusinessTheme
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.SuperAdminViewModel
import androidx.compose.ui.platform.LocalContext

enum class AppScreen {
    LOGIN,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD,
    SALESMAN_DASHBOARD,
    NEW_SALE,
    COLLECT_PAYMENT,
    SALE_RECEIPT,
    MONEY_RECEIPT,
    PRODUCTS_STOCK,
    CUSTOMERS,
    EXPENSES,
    SALES_LIST,
    RECEIPTS_LIST,
    REPORTS,
    SETTINGS,
    SALESMEN_MANAGEMENT,
    AUDIT_LOGS,
    AI_ASSISTANT,
    SALESMAN_PRODUCT_SUBMISSION,
    SALESMAN_SUBMITTED_PRODUCTS,
    ADMIN_PRODUCT_APPROVAL,
    COMPANY_DUE,
    DATA_PROTECTION_CENTER,
    SMART_REMINDER,
    ADMIN_ALERT,
    SALESMAN_ALERT,
    ADMIN_STOCK_CONTROL,
    SALESMAN_STOCK_VERIFICATION,
    SUPER_ADMIN_DASHBOARD,
    SHOP_REGISTRATION
}

class MainActivity : ComponentActivity() {
    private val viewModel: BusinessViewModel by viewModels()
    private val superAdminViewModel: SuperAdminViewModel by viewModels {
        SuperAdminViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure global Coil ImageLoader with standard User-Agent so Wikimedia Commons and external CDNs never return HTTP 403
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "SmartBusinessManagerApp/1.0 (Android; support@smartbiz.local)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .build()
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            SmartBusinessTheme {
                SmartBusinessApp(
                    viewModel = viewModel,
                    superAdminViewModel = superAdminViewModel
                )
            }
        }
    }
}

@Composable
fun SmartBusinessApp(
    viewModel: BusinessViewModel,
    superAdminViewModel: SuperAdminViewModel? = null
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }

    // Screen State parameters
    var activeSale by remember { mutableStateOf<Sale?>(null) }
    var activeSaleItems by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
    var activeReceipt by remember { mutableStateOf<PaymentReceipt?>(null) }
    var preselectedCustomerIdForPayment by remember { mutableStateOf<String?>(null) }
    var activeSubmissionToResubmit by remember { mutableStateOf<ProductSubmission?>(null) }
    var dataProtectionPreviousScreen by remember { mutableStateOf(AppScreen.SETTINGS) }

    // Synchronize authentication with screen and enforce role-based navigation security
    LaunchedEffect(currentUser, currentScreen) {
        if (currentUser == null) {
            if (currentScreen != AppScreen.LOGIN && currentScreen != AppScreen.ADMIN_LOGIN && currentScreen != AppScreen.SHOP_REGISTRATION) {
                currentScreen = AppScreen.LOGIN
            }
        } else {
            val role = currentUser?.role
            val isSuperAdmin = viewModel.isSuperAdminUser()
            val adminOnlyScreens = setOf(
                AppScreen.ADMIN_DASHBOARD,
                AppScreen.SETTINGS,
                AppScreen.SALESMEN_MANAGEMENT,
                AppScreen.AUDIT_LOGS,
                AppScreen.ADMIN_PRODUCT_APPROVAL,
                AppScreen.COMPANY_DUE,
                AppScreen.DATA_PROTECTION_CENTER
            )
            // Strict Platform Super Admin Protection: regular shop admins and salesmen are never allowed in SUPER_ADMIN_DASHBOARD
            if (currentScreen == AppScreen.SUPER_ADMIN_DASHBOARD && !isSuperAdmin) {
                currentScreen = if (role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
            } else if (role != UserRole.ADMIN && !isSuperAdmin && currentScreen in adminOnlyScreens) {
                currentScreen = AppScreen.SALESMAN_DASHBOARD
            } else if (currentScreen == AppScreen.LOGIN || currentScreen == AppScreen.ADMIN_LOGIN) {
                currentScreen = if (isSuperAdmin) {
                    AppScreen.SUPER_ADMIN_DASHBOARD
                } else if (role == UserRole.ADMIN) {
                    AppScreen.ADMIN_DASHBOARD
                } else {
                    AppScreen.SALESMAN_DASHBOARD
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentUser != null && (currentScreen == AppScreen.ADMIN_DASHBOARD || currentScreen == AppScreen.SALESMAN_DASHBOARD || currentScreen == AppScreen.SALES_LIST || currentScreen == AppScreen.RECEIPTS_LIST)) {
                val isAdmin = currentUser?.role == UserRole.ADMIN
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.ADMIN_DASHBOARD || currentScreen == AppScreen.SALESMAN_DASHBOARD,
                        onClick = {
                            currentScreen = if (isAdmin) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "হোম") },
                        label = { Text("হোম") }
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SALES_LIST,
                        onClick = { currentScreen = AppScreen.SALES_LIST },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "বিক্রি") },
                        label = { Text("বিক্রি") }
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.RECEIPTS_LIST,
                        onClick = { currentScreen = AppScreen.RECEIPTS_LIST },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = "রসিদ") },
                        label = { Text("রসিদ") }
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            viewModel.logout()
                            currentScreen = AppScreen.LOGIN
                        },
                        icon = { Icon(Icons.Default.Logout, contentDescription = "লগআউট", tint = MaterialTheme.colorScheme.error) },
                        label = { Text("লগআউট", color = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.LOGIN -> {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToAdminLogin = {
                            currentScreen = AppScreen.ADMIN_LOGIN
                        },
                        onNavigateToRegistration = {
                            currentScreen = AppScreen.SHOP_REGISTRATION
                        },
                        onLoginSuccess = {
                            currentScreen = if (viewModel.isSuperAdminUser()) {
                                AppScreen.SUPER_ADMIN_DASHBOARD
                            } else if (viewModel.currentUser.value?.role == UserRole.ADMIN) {
                                AppScreen.ADMIN_DASHBOARD
                            } else {
                                AppScreen.SALESMAN_DASHBOARD
                            }
                        }
                    )
                }

                AppScreen.ADMIN_LOGIN -> {
                    AdminLoginScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = AppScreen.LOGIN
                        },
                        onNavigateToRegistration = {
                            currentScreen = AppScreen.SHOP_REGISTRATION
                        },
                        onAdminLoginSuccess = {
                            currentScreen = if (viewModel.isSuperAdminUser()) {
                                AppScreen.SUPER_ADMIN_DASHBOARD
                            } else {
                                AppScreen.ADMIN_DASHBOARD
                            }
                        }
                    )
                }

                AppScreen.SHOP_REGISTRATION -> {
                    ShopRegistrationScreen(
                        viewModel = viewModel,
                        superAdminViewModel = superAdminViewModel,
                        onBack = {
                            currentScreen = AppScreen.LOGIN
                        },
                        onRegistrationSuccess = {
                            currentScreen = AppScreen.ADMIN_DASHBOARD
                        }
                    )
                }

                AppScreen.ADMIN_DASHBOARD -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onNavigateToSales = { currentScreen = AppScreen.SALES_LIST },
                            onNavigateToNewSale = { currentScreen = AppScreen.NEW_SALE },
                            onNavigateToProducts = { currentScreen = AppScreen.PRODUCTS_STOCK },
                            onNavigateToCustomers = { currentScreen = AppScreen.CUSTOMERS },
                            onNavigateToCollectPayment = {
                                preselectedCustomerIdForPayment = null
                                currentScreen = AppScreen.COLLECT_PAYMENT
                            },
                            onNavigateToReceipts = { currentScreen = AppScreen.RECEIPTS_LIST },
                            onNavigateToExpenses = { currentScreen = AppScreen.EXPENSES },
                            onNavigateToProductApprovals = { currentScreen = AppScreen.ADMIN_PRODUCT_APPROVAL },
                            onNavigateToSalesmen = { currentScreen = AppScreen.SALESMEN_MANAGEMENT },
                            onNavigateToReports = { currentScreen = AppScreen.REPORTS },
                            onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                            onNavigateToAuditLogs = { currentScreen = AppScreen.AUDIT_LOGS },
                            onNavigateToAiAssistant = { currentScreen = AppScreen.AI_ASSISTANT },
                            onNavigateToCompanyDue = { currentScreen = AppScreen.COMPANY_DUE },
                            onNavigateToDataProtectionCenter = { currentScreen = AppScreen.DATA_PROTECTION_CENTER },
                            onNavigateToReminders = { currentScreen = AppScreen.SMART_REMINDER },
                            onNavigateToSalesmanAlerts = { currentScreen = AppScreen.ADMIN_ALERT },
                            onNavigateToStockControl = { currentScreen = AppScreen.ADMIN_STOCK_CONTROL },
                            onNavigateToSuperAdmin = { currentScreen = AppScreen.SUPER_ADMIN_DASHBOARD }
                        )
                    }
                }

                AppScreen.SALESMAN_DASHBOARD -> {
                    SalesmanDashboardScreen(
                        viewModel = viewModel,
                        onNavigateToNewSale = { currentScreen = AppScreen.NEW_SALE },
                        onNavigateToStock = { currentScreen = AppScreen.PRODUCTS_STOCK },
                        onNavigateToCollectPayment = {
                            preselectedCustomerIdForPayment = null
                            currentScreen = AppScreen.COLLECT_PAYMENT
                        },
                        onNavigateToMyCustomers = { currentScreen = AppScreen.CUSTOMERS },
                        onNavigateToSubmitProduct = {
                            activeSubmissionToResubmit = null
                            currentScreen = AppScreen.SALESMAN_PRODUCT_SUBMISSION
                        },
                        onNavigateToMySubmittedProducts = {
                            currentScreen = AppScreen.SALESMAN_SUBMITTED_PRODUCTS
                        },
                        onNavigateToMySales = { currentScreen = AppScreen.SALES_LIST },
                        onNavigateToMyReceipts = { currentScreen = AppScreen.RECEIPTS_LIST },
                        onNavigateToExpenseApply = { currentScreen = AppScreen.EXPENSES },
                        onNavigateToMyReport = { currentScreen = AppScreen.REPORTS },
                        onNavigateToAiAssistant = { currentScreen = AppScreen.AI_ASSISTANT },
                        onNavigateToReminders = { currentScreen = AppScreen.SMART_REMINDER },
                        onNavigateToAlerts = { currentScreen = AppScreen.SALESMAN_ALERT },
                        onNavigateToStockVerification = { currentScreen = AppScreen.SALESMAN_STOCK_VERIFICATION }
                    )
                }

                AppScreen.NEW_SALE -> {
                    NewSaleScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onSaleSuccess = { sale, items ->
                            activeSale = sale
                            activeSaleItems = items
                            currentScreen = AppScreen.SALE_RECEIPT
                        }
                    )
                }

                AppScreen.COLLECT_PAYMENT -> {
                    CollectPaymentScreen(
                        viewModel = viewModel,
                        preselectedCustomerId = preselectedCustomerIdForPayment,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onReceiptCreated = { receipt ->
                            activeReceipt = receipt
                            currentScreen = AppScreen.MONEY_RECEIPT
                        }
                    )
                }

                AppScreen.SALE_RECEIPT -> {
                    activeSale?.let { sale ->
                        SaleReceiptViewScreen(
                            sale = sale,
                            items = activeSaleItems,
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.MONEY_RECEIPT -> {
                    activeReceipt?.let { receipt ->
                        MoneyReceiptViewScreen(
                            receipt = receipt,
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.PRODUCTS_STOCK -> {
                    ProductsStockScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onSaleSuccess = { sale, items ->
                            activeSale = sale
                            activeSaleItems = items
                            currentScreen = AppScreen.SALE_RECEIPT
                        }
                    )
                }

                AppScreen.CUSTOMERS -> {
                    CustomersScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onCollectPaymentForCustomer = { custId ->
                            preselectedCustomerIdForPayment = custId
                            currentScreen = AppScreen.COLLECT_PAYMENT
                        }
                    )
                }

                AppScreen.EXPENSES -> {
                    ExpensesScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.SALES_LIST -> {
                    SalesListScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onSaleClick = { sale, items ->
                            activeSale = sale
                            activeSaleItems = items
                            currentScreen = AppScreen.SALE_RECEIPT
                        }
                    )
                }

                AppScreen.RECEIPTS_LIST -> {
                    ReceiptsListScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onReceiptClick = { receipt ->
                            activeReceipt = receipt
                            currentScreen = AppScreen.MONEY_RECEIPT
                        }
                    )
                }

                AppScreen.REPORTS -> {
                    ReportsScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.SETTINGS -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            },
                            onNavigateToDataProtectionCenter = {
                                currentScreen = AppScreen.DATA_PROTECTION_CENTER
                            }
                        )
                    }
                }

                AppScreen.SALESMEN_MANAGEMENT -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        SalesmenManagementScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.AUDIT_LOGS -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        AuditLogsScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.AI_ASSISTANT -> {
                    AiAssistantScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.SALESMAN_PRODUCT_SUBMISSION -> {
                    SalesmanProductSubmissionScreen(
                        viewModel = viewModel,
                        submissionToEdit = activeSubmissionToResubmit,
                        onBack = {
                            currentScreen = if (activeSubmissionToResubmit != null) AppScreen.SALESMAN_SUBMITTED_PRODUCTS else AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.SALESMAN_SUBMITTED_PRODUCTS -> {
                    SalesmanSubmittedProductsScreen(
                        viewModel = viewModel,
                        onNavigateToSubmitNew = {
                            activeSubmissionToResubmit = null
                            currentScreen = AppScreen.SALESMAN_PRODUCT_SUBMISSION
                        },
                        onNavigateToResubmit = { sub ->
                            activeSubmissionToResubmit = sub
                            currentScreen = AppScreen.SALESMAN_PRODUCT_SUBMISSION
                        },
                        onBack = {
                            currentScreen = AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.ADMIN_PRODUCT_APPROVAL -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        AdminProductApprovalScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.COMPANY_DUE -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        CompanyDueScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.DATA_PROTECTION_CENTER -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        DataProtectionCenterScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.SETTINGS
                            }
                        )
                    }
                }

                AppScreen.SMART_REMINDER -> {
                    SmartReminderScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        },
                        onNavigateToRoute = { route ->
                            when (route) {
                                "PRODUCTS_STOCK" -> currentScreen = AppScreen.PRODUCTS_STOCK
                                "EXPENSES" -> currentScreen = AppScreen.EXPENSES
                                "ADMIN_PRODUCT_APPROVAL" -> {
                                    currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_PRODUCT_APPROVAL else AppScreen.SALESMAN_SUBMITTED_PRODUCTS
                                }
                                "SALESMAN_SUBMITTED_PRODUCTS" -> currentScreen = AppScreen.SALESMAN_SUBMITTED_PRODUCTS
                                "CUSTOMERS" -> currentScreen = AppScreen.CUSTOMERS
                                "REPORTS" -> currentScreen = AppScreen.REPORTS
                                "ADMIN_ALERT" -> currentScreen = AppScreen.ADMIN_ALERT
                                "SALESMAN_ALERTS" -> currentScreen = AppScreen.SALESMAN_ALERT
                                "ADMIN_STOCK_CONTROL" -> currentScreen = AppScreen.ADMIN_STOCK_CONTROL
                                "SALESMAN_STOCK_VERIFICATION" -> currentScreen = AppScreen.SALESMAN_STOCK_VERIFICATION
                                else -> {
                                    currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                                }
                            }
                        }
                    )
                }

                AppScreen.ADMIN_ALERT -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        AdminAlertScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.SALESMAN_ALERT -> {
                    SalesmanAlertScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.ADMIN_STOCK_CONTROL -> {
                    if (currentUser?.role != UserRole.ADMIN) {
                        currentScreen = AppScreen.SALESMAN_DASHBOARD
                    } else {
                        AdminStockControlScreen(
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            }
                        )
                    }
                }

                AppScreen.SALESMAN_STOCK_VERIFICATION -> {
                    SalesmanStockVerificationScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        }
                    )
                }

                AppScreen.SUPER_ADMIN_DASHBOARD -> {
                    androidx.activity.compose.BackHandler {
                        currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                    }
                    if (!viewModel.isSuperAdminUser()) {
                        LaunchedEffect(Unit) {
                            Toast.makeText(
                                context,
                                "অননুমোদিত প্রবেশাধিকার! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন প্রবেশ করতে পারবেন।",
                                Toast.LENGTH_LONG
                            ).show()
                            currentScreen = if (currentUser?.role == UserRole.ADMIN) AppScreen.ADMIN_DASHBOARD else AppScreen.SALESMAN_DASHBOARD
                        }
                    } else {
                        val activeSuperAdminVm = superAdminViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = SuperAdminViewModel.provideFactory(
                                context.applicationContext as android.app.Application
                            )
                        )
                        SuperAdminDashboardScreen(
                            currentSuperAdminEmail = viewModel.getSuperAdminEmail(),
                            onBackToStoreAdmin = {
                                currentScreen = AppScreen.ADMIN_DASHBOARD
                            },
                            viewModel = activeSuperAdminVm
                        )
                    }
                }
            }
        }
    }
}
