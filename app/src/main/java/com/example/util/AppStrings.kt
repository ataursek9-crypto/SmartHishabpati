package com.example.util

/**
 * Complete Bengali and English strings repository for Smart Business Manager.
 * All texts dynamically change when language is switched between Bengali and English.
 */
object AppStrings {

    private fun isEn(): Boolean = AppLanguageManager.isEnglish()

    // App Branding & Slogan
    val appName: String get() = if (isEn()) "SmartHishabpati" else "SmartHishabpati"
    val appSlogan: String get() = if (isEn()) "— Manage Business Smartly —" else "— হিসাব রাখুন স্মার্টভাবে —"
    val securityBanner: String get() = if (isEn()) "Your accounts are secure & encrypted" else "আপনার হিসাব নিরাপদ ও সুরক্ষিত"

    // Common Actions
    val ok: String get() = if (isEn()) "OK" else "ঠিক আছে"
    val cancel: String get() = if (isEn()) "Cancel" else "বাতিল"
    val save: String get() = if (isEn()) "Save" else "সংরক্ষণ"
    val submit: String get() = if (isEn()) "Submit" else "জমা দিন"
    val edit: String get() = if (isEn()) "Edit" else "সম্পাদনা"
    val delete: String get() = if (isEn()) "Delete" else "মুছুন"
    val confirm: String get() = if (isEn()) "Confirm" else "নিশ্চিত করুন"
    val back: String get() = if (isEn()) "Back" else "ফিরে যান"
    val retry: String get() = if (isEn()) "Retry" else "পুনরায় চেষ্টা করুন"
    val search: String get() = if (isEn()) "Search..." else "অনুসন্ধান..."
    val loading: String get() = if (isEn()) "Loading..." else "অপেক্ষা করুন..."
    val close: String get() = if (isEn()) "Close" else "বন্ধ করুন"
    val copy: String get() = if (isEn()) "Copy" else "কপি করুন"

    // Navigation Items
    val navHome: String get() = if (isEn()) "Home" else "হোম"
    val navSales: String get() = if (isEn()) "Sales" else "বিক্রি"
    val navReceipts: String get() = if (isEn()) "Receipts" else "রসিদ"
    val navLogout: String get() = if (isEn()) "Logout" else "লগআউট"
    val navSettings: String get() = if (isEn()) "Settings" else "সেটিংস"
    val navReports: String get() = if (isEn()) "Reports" else "রিপোর্ট"

    // Authentication Tabs & Inputs
    val tabGmailLogin: String get() = if (isEn()) "Gmail Login" else "Gmail লগইন"
    val tabPhoneLogin: String get() = if (isEn()) "Phone Login" else "মোবাইল লগইন"
    val labelEmail: String get() = if (isEn()) "Gmail / Email Address" else "Gmail / ইমেইল ঠিকানা"
    val hintEmail: String get() = if (isEn()) "e.g. user@gmail.com" else "যেমন: user@gmail.com"
    val labelPassword: String get() = if (isEn()) "Password" else "পাসওয়ার্ড"
    val hintPassword: String get() = if (isEn()) "Enter password" else "পাসওয়ার্ড লিখুন"
    val labelPhone: String get() = if (isEn()) "Mobile Number" else "মোবাইল নম্বর"
    val hintPhone: String get() = if (isEn()) "017XXXXXXXX" else "০১৭১১XXXXXX"
    val labelPin: String get() = if (isEn()) "Security PIN (6 Digits)" else "পিন নম্বর (৬ ডিজিট)"
    val hintPin: String get() = if (isEn()) "Enter 6-digit PIN" else "৬ সংখ্যার পিন লিখুন"
    val rememberMe: String get() = if (isEn()) "Remember Me" else "মনে রাখুন (Remember Me)"
    val forgotPin: String get() = if (isEn()) "Forgot PIN?" else "পিন ভুলে গেছেন?"
    val forgotPassword: String get() = if (isEn()) "Forgot Password?" else "পাসওয়ার্ড ভুলে গেছেন?"
    val needHelp: String get() = if (isEn()) "Need Help?" else "সাহায্য প্রয়োজন?"
    val createNewShop: String get() = if (isEn()) "Create New Account / Register Shop" else "নতুন অ্যাকাউন্ট তৈরি করুন / দোকান নিবন্ধন"
    val adminLoginPortal: String get() = if (isEn()) "Admin Login Portal" else "এডমিন লগইন পোর্টাল"
    val buttonLogin: String get() = if (isEn()) "Log In" else "লগইন করুন"

    // Google Sign-In
    val googleSignInButton: String get() = if (isEn()) "Sign In with Google" else "Google অ্যাকাউন্ট দিয়ে সাইন ইন"
    val googleSignInSubtitle: String get() = if (isEn()) "Fast, secure one-click sign in" else "দ্রুত ও নিরাপদ এক-ক্লিকে সাইন ইন"
    val googleAccountLinkingTitle: String get() = if (isEn()) "Link Google Account to Shop" else "দোকানের সাথে গুগল অ্যাকাউন্ট লিংক"

    // Phone OTP & Verification
    val sendOtpButton: String get() = if (isEn()) "Send OTP Code" else "ওটিপি (OTP) কোড পাঠান"
    val verifyOtpButton: String get() = if (isEn()) "Verify & Sign In" else "যাচাই করুন ও প্রবেশ করুন"
    val resendOtpText: String get() = if (isEn()) "Resend OTP" else "পুনরায় কোড পাঠান"
    val otpSentSuccess: String get() = if (isEn()) "Verification code sent to your phone" else "আপনার নম্বরে ওটিপি কোড পাঠানো হয়েছে"

    // Shop Registration Wizard
    val regStep1Title: String get() = if (isEn()) "Owner Verification" else "মালিকের পরিচয় যাচাই"
    val regStep2Title: String get() = if (isEn()) "Shop Information" else "দোকানের সাধারণ তথ্য"
    val regStep3Title: String get() = if (isEn()) "Security & PIN" else "নিরাপত্তা ও গোপন পিন"
    val regStep4Title: String get() = if (isEn()) "Confirmation" else "নিবন্ধন নিশ্চিতকরণ"
    val labelShopName: String get() = if (isEn()) "Shop / Business Name" else "দোকান / ব্যবসার নাম"
    val labelOwnerName: String get() = if (isEn()) "Owner's Name" else "মালিকের নাম"
    val labelShopAddress: String get() = if (isEn()) "Shop Address" else "দোকানের ঠিকানা"
    val buttonRegisterShop: String get() = if (isEn()) "Complete Registration" else "নিবন্ধন সম্পন্ন করুন"

    // Shop Selection
    val shopSelectionTitle: String get() = if (isEn()) "Select Your Shop" else "আপনার দোকান নির্বাচন করুন"
    val shopSelectionSubtitle: String get() = if (isEn()) "Multiple shops linked with your account" else "আপনার অ্যাকাউন্টের সাথে একাধিক দোকান যুক্ত আছে"

    // Dashboard Items
    val adminDashboardTitle: String get() = if (isEn()) "Admin Dashboard" else "এডমিন ড্যাশবোর্ড"
    val salesmanDashboardTitle: String get() = if (isEn()) "Sales Representative Dashboard" else "বিক্রয় প্রতিনিধি ড্যাশবোর্ড"
    val todaysSales: String get() = if (isEn()) "Today's Sales" else "আজকের মোট বিক্রি"
    val todaysCollection: String get() = if (isEn()) "Today's Collection" else "আজকের মোট আদায়"
    val totalDue: String get() = if (isEn()) "Total Customer Due" else "মোট কাস্টমার বকেয়া"
    val lowStockAlert: String get() = if (isEn()) "Low Stock Alert" else "কম স্টক সতর্কতা"
    val quickSale: String get() = if (isEn()) "New Sale" else "নতুন বিক্রি"
    val collectPayment: String get() = if (isEn()) "Collect Payment" else "বকেয়া আদায়"
    val manageProducts: String get() = if (isEn()) "Products & Stock" else "পণ্য ও স্টক"
    val manageCustomers: String get() = if (isEn()) "Customer List" else "কাস্টমার তালিকা"
    val manageExpenses: String get() = if (isEn()) "Daily Expenses" else "দৈনিক খরচ"
    val companyDue: String get() = if (isEn()) "Supplier / Company Due" else "কোম্পানি / মহাজন বকেয়া"
    val smartReminders: String get() = if (isEn()) "Smart Reminders" else "স্মার্ট রিমাইন্ডার"

    // Products & Stock
    val addProduct: String get() = if (isEn()) "Add New Product" else "নতুন পণ্য যোগ"
    val productName: String get() = if (isEn()) "Product Name" else "পণ্যের নাম"
    val productCategory: String get() = if (isEn()) "Category" else "ক্যাটাগরি"
    val purchasePrice: String get() = if (isEn()) "Purchase Price (৳)" else "ক্রয় মূল্য (৳)"
    val salePrice: String get() = if (isEn()) "Sale Price (৳)" else "বিক্রয় মূল্য (৳)"
    val currentStock: String get() = if (isEn()) "Available Stock" else "বর্তমান স্টক"
    val stockAlertThreshold: String get() = if (isEn()) "Low Stock Alert Threshold" else "সর্বনিম্ন সতর্কতা স্টক"

    // New Sale
    val saleCartTitle: String get() = if (isEn()) "Sales Cart" else "বিক্রির তালিকা"
    val selectCustomer: String get() = if (isEn()) "Select Customer" else "কাস্টমার নির্বাচন করুন"
    val walkInCustomer: String get() = if (isEn()) "Walk-in / Cash Customer" else "সাধারণ নগদ কাস্টমার"
    val subtotal: String get() = if (isEn()) "Subtotal" else "উপ-মোট"
    val discount: String get() = if (isEn()) "Discount" else "ডিসকাউন্ট"
    val totalBill: String get() = if (isEn()) "Total Bill" else "সর্বমোট বিল"
    val paidAmount: String get() = if (isEn()) "Received Amount" else "জমা / প্রাপ্ত টাকা"
    val dueAmount: String get() = if (isEn()) "Due Amount" else "বকেয়া টাকা"
    val completeSaleButton: String get() = if (isEn()) "Confirm & Print Receipt" else "বিক্রি নিশ্চিত ও রসিদ তৈরি"

    // Customers & Due
    val customerName: String get() = if (isEn()) "Customer Name" else "কাস্টমারের নাম"
    val customerPhone: String get() = if (isEn()) "Customer Phone" else "কাস্টমারের মোবাইল"
    val customerAddress: String get() = if (isEn()) "Address" else "ঠিকানা"
    val previousDue: String get() = if (isEn()) "Previous Due" else "পূর্ববর্তী বকেয়া"
    val currentDue: String get() = if (isEn()) "Current Due" else "বর্তমান বকেয়া"

    // Collections & Receipts
    val receiptNumber: String get() = if (isEn()) "Receipt No" else "রসিদ নম্বর"
    val paymentMethod: String get() = if (isEn()) "Payment Method" else "পেমেন্ট মাধ্যম"
    val cash: String get() = if (isEn()) "Cash" else "নগদ (Cash)"
    val bKash: String get() = if (isEn()) "bKash" else "বিকাশ (bKash)"
    val nagad: String get() = if (isEn()) "Nagad" else "নগদ (Nagad)"
    val bank: String get() = if (isEn()) "Bank" else "ব্যাংক (Bank)"
    val sharePdfReceipt: String get() = if (isEn()) "Share PDF Receipt" else "পিডিএফ রসিদ শেয়ার করুন"

    // Expenses
    val expenseCategory: String get() = if (isEn()) "Expense Category" else "খরচের খাত"
    val expenseAmount: String get() = if (isEn()) "Expense Amount (৳)" else "খরচের পরিমাণ (৳)"
    val expenseNote: String get() = if (isEn()) "Note / Description" else "বিবরণ / নোট"

    // Data Protection & Cloud Sync
    val cloudSyncStatus: String get() = if (isEn()) "Cloud Data Sync" else "ক্লাউড ডেটা সিঙ্ক"
    val syncConnected: String get() = if (isEn()) "Connected & Synchronized" else "সার্ভার সংযুক্ত ও সুরক্ষিত"
    val syncOffline: String get() = if (isEn()) "Offline Mode Active" else "অফলাইন মোড সক্রিয়"
    val lastSyncTime: String get() = if (isEn()) "Last Synced" else "সর্বশেষ সিঙ্ক"

    // Super Admin
    val superAdminPortalTitle: String get() = if (isEn()) "Platform Super Admin Portal" else "প্ল্যাটফর্ম সুপার এডমিন পোর্টাল"
    val registeredShopsCount: String get() = if (isEn()) "Total Registered Shops" else "মোট নিবন্ধিত দোকান"
    val allowlistVerified: String get() = if (isEn()) "Allowlist Verified" else "অনুমোদিত এডমিন তালিকাভুক্ত"

    // Error & Support Headings
    val errorProblemTitle: String get() = if (isEn()) "Problem:" else "সমস্যা:"
    val errorActionTitle: String get() = if (isEn()) "What to do:" else "করণীয়:"

    // Password Reset & Phone OTP Specific
    val passwordResetRequestAccepted: String get() = if (isEn()) {
        "Your password reset request has been accepted by Firebase. Please check your Gmail Inbox, Spam, and Promotions folders.\n(Note: If this account was created with Google, no password is needed—you can sign in directly with Google)."
    } else {
        "পাসওয়ার্ড পরিবর্তনের লিংক পাঠানোর অনুরোধ Firebase-এ গৃহীত হয়েছে। আপনার Gmail-এর ইনবক্স (Inbox), Spam ও Promotions ফোল্ডার পরীক্ষা করুন।\n(নোট: যদি অ্যাকাউন্টটি Google দিয়ে তৈরি হয়ে থাকে, তবে কোনো পাসওয়ার্ড পরিবর্তনের প্রয়োজন নেই—সরাসরি Google দিয়ে প্রবেশ করতে পারবেন)।"
    }

    val googleOnlyAccountNotice: String get() = if (isEn()) {
        "This account was created with Google Sign-In and has no password. Please use the 'Continue with Google' button below."
    } else {
        "এই অ্যাকাউন্টটি 'Google Sign-In' দিয়ে তৈরি। এতে কোনো পাসওয়ার্ড নেই। অনুগ্রহ করে 'Google দিয়ে প্রবেশ' বোতামে চাপ দিন।"
    }

    val continueWithGoogle: String get() = if (isEn()) "Continue with Google" else "Google দিয়ে প্রবেশ করুন"

    val emailDeliveryNotice: String get() = if (isEn()) {
        "Notice: Firebase has accepted the request. Check your Gmail Inbox, Spam, and Promotions folders. (Emails are sent from noreply@smart-business-manager-71879.firebaseapp.com)."
    } else {
        "দৃষ্টি আকর্ষণ: Firebase অনুরোধটি গ্রহণ করেছে। আপনার Gmail-এর Inbox, Spam ও Promotions ফোল্ডার চেক করুন। (ইমেইলটি noreply@smart-business-manager-71879.firebaseapp.com থেকে পাঠানো হয়)।"
    }

    val phoneOtpNotice: String get() = if (isEn()) {
        "SMS OTP dispatch request accepted by Firebase. Please wait for the SMS on your mobile network."
    } else {
        "Firebase সার্ভারে এসএমএস ওটিপি পাঠানোর অনুরোধ গৃহীত হয়েছে। আপনার মোবাইল নেটওয়ার্কে এসএমএস আসা অপেক্ষা করুন।"
    }
}
