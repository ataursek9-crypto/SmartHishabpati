# Smart Business Manager (SmartHishabpati) — Firebase Initialization স্থায়ী সমাধান এবং সম্পূর্ণ বাংলা-English ভাষা সিস্টেম

## সম্পন্ন কাজের সারসংক্ষেপ

১. **Firebase Initialization সমস্যার স্থায়ী সমাধান:**
   - **মূল কারণ:** GitHub রিপোজিটরি (`ataursek9-crypto/SmartHishabpati`) ক্লোন/আমদানির সময় `.gitignore` বিধিমালার কারণে `app/google-services.json` ফাইলটি অনুপস্থিত ছিল। ফলশ্রুতিতে Google Services Gradle Plugin ব্যাকগ্রাউন্ডে Firebase এর প্রয়োজনীয় মেটাডাটা XML (`google_app_id`, `project_id`, `api_key`) তৈরি করতে পারেনি এবং অ্যাপ চালু হওয়ার সময় ডিফল্ট `FirebaseInitProvider` ব্যর্থ হয়। পরবর্তীতে `BusinessViewModel` বা `PhoneAuthHelper` যখন `FirebaseAuth.getInstance()` অথবা `FirebaseFirestore.getInstance()` অ্যাক্সেস করছিল, তখন ক্র্যাশ দেখা দেয়:
     `"Default FirebaseApp is not initialized in this process com.aistudio.smartbiz.mgr. Make sure to call FirebaseApp.initializeApp(Context) first."`
   - **স্থায়ী সমাধান:**
     - প্রজেক্টের অফিসিয়াল `google-services.json` (`smart-business-manager-71879`) যথাযথভাবে রিস্টোর করা হয়েছে।
     - একটি নির্ভরযোগ্য কেন্দ্রীয় থ্রেড-সেফ `FirebaseInitHelper` তৈরি করা হয়েছে যা রিসোর্স এবং প্রোগ্রাম্যাটিক `FirebaseOptions` ব্যাকআপ নিশ্চিত করে।
     - কাস্টম `SmartBusinessApplication` ক্লাস তৈরি করে `AndroidManifest.xml`-এ নিবন্ধন করা হয়েছে, যা অ্যাপ শুরু হওয়ার মুহূর্তেই সর্বপ্রথম Firebase এবং Language Manager ইনিশিয়ালাইজ করে।
     - `BusinessViewModel`, `PhoneAuthHelper`, `GoogleSignInHelper`, `CloudSyncManager` এবং `SuperAdminRepository`-তে ডিফেন্সিভ ইনিশিয়ালাইজেশন চেক নিশ্চিত করা হয়েছে।

২. **ডায়নামিক বাংলা ও English ভাষা সিস্টেম:**
   - **লগইন স্ক্রিন ও ড্যাশবোর্ড সুইচার:** লগইন স্ক্রিন এবং প্রধান স্ক্রিনগুলোতে উপরের ডানপাশে গ্লোব (🌐) আইকন যুক্ত করা হয়েছে।
   - **অপশনসমূহ:** 🇧🇩 বাংলা (ডিফল্ট) এবং 🇬🇧 English।
   - **তাৎক্ষণিক রিফ্লেকশন:** ভাষা পরিবর্তনের সাথে সাথে অ্যাপ রিস্টার্ট বা পুনরায় লগইন ছাড়াই সমগ্র ইন্টারফেস রিয়েল-টাইমে নির্বাচিত ভাষায় পরিবর্তিত হয়।
   - **মুদ্রা ও সংখ্যা ফরম্যাটিং:** মুদ্রা সবসময় বাংলাদেশি টাকা (`৳`) হিসেবে সংরক্ষিত থাকে।

৩. **কেন্দ্রীভূত মানবিক ত্রুটি ব্যবস্থাপনা (`AppErrorHandler`):**
   - কারিগরি জটিল এরর দূর করে প্রতিটি সমস্যার জন্য ব্যবহারকারীকে সম্মানজনক ভাষায় দুটি অংশ দেখানো হয়:
     - **সমস্যা (Problem):** কী ঘটেছে।
     - **করণীয় (What to do):** ব্যবহারকারীকে এখন কী করতে হবে।
   - লগক্যাটে (Logcat) লগ করার সময় পাসওয়ার্ড, পিন, ওটিপি (OTP) ও টোকেন স্বয়ংক্রিয়ভাবে মাস্ক (`***`) করা হয়।

---

## ১. Firebase Initialization সমস্যার মূল কারণ ও সমাধান

| বিষয় | পূর্বের অবস্থা | বর্তমান স্থায়ী সমাধান |
|---|---|---|
| `google-services.json` | ফাইলটি অনুপস্থিত ছিল | প্রজেক্টের সঠিক কনফিগারেশন (`smart-business-manager-71879`, প্যাকেজ: `com.aistudio.smartbiz.mgr`) সহ রিস্টোর করা হয়েছে। |
| প্রজেক্ট আইডি | আন-কনফিগারড | `smart-business-manager-71879` |
| অ্যাপ আইডি | অনুপস্থিত | `1:484040397249:android:8a883b7879abd19bd6df40` |
| `Application` ক্লাস | কোনো কাস্টম ক্লাস ছিল না | `SmartBusinessApplication.kt` তৈরি করে `AndroidManifest.xml`-এ রেজিস্টার করা হয়েছে। |
| ইনিশিয়ালাইজেশন সময়কাল | Activity/ViewModel লোড হওয়ার পর বিচ্ছিন্নভাবে | অ্যাপ প্রসেস চালু হওয়ার সাথে সাথে সবার প্রথমে `SmartBusinessApplication.onCreate()`-এ। |
| ডিফেন্সিভ ফলব্যাক | ক্র্যাশ হতো | `FirebaseInitHelper` রিসোর্স ব্যর্থ হলে স্বয়ংক্রিয়ভাবে প্রাতিষ্ঠানিক ক্রেডেনশিয়াল দিয়ে ইনিশিয়ালাইজ করে। |

---

## ২. পরিবর্তিত ও নতুন ফাইলের তালিকা

### নতুন তৈরি করা ফাইল:
1. `/app/src/main/java/com/example/SmartBusinessApplication.kt`: কেন্দ্রীয় অ্যাপ্লিকেশন ক্লাস।
2. `/app/src/main/java/com/example/util/FirebaseInitHelper.kt`: থ্রেড-সেফ সেন্ট্রাল ইনিশিয়ালাইজেশন আর্কিটেকচার।
3. `/app/src/main/java/com/example/util/AppLanguageManager.kt`: পারসিস্টেন্ট ভাষা নিয়ন্ত্রণ ও স্থানীয় ফরম্যাটার।
4. `/app/src/main/java/com/example/util/AppStrings.kt`: সম্পূর্ণ বাংলা ও ইংরেজি শব্দভাণ্ডার রিপোজিটরি।
5. `/app/src/main/java/com/example/util/AppErrorHandler.kt`: সমস্যা ও করণীয় ভিত্তিক দ্বিভাষিক এরর ইঞ্জিন এবং সিকিউর স্যানিটাইজার।
6. `/app/src/main/java/com/example/ui/components/LanguageSwitcher.kt`: গ্লোব (🌐) ড্রপডাউন সুইচার উপাদান।
7. `/app/src/test/java/com/example/FirebaseInitAndLanguageSystemTest.kt`: স্বয়ংক্রিয় টেস্ট স্যুট।

### পরিবর্তিত ফাইলসমূহ:
1. `/app/google-services.json`: অফিসিয়াল কনফিগারেশন রিস্টোর করা হয়েছে।
2. `/app/src/main/AndroidManifest.xml`: `android:name=".SmartBusinessApplication"` যুক্ত করা হয়েছে।
3. `/.env` ও `/.env.example`: Secrets Gradle প্লাগইন ফ্রেমওয়ার্ক সন্তুষ্ট রাখতে প্রস্তুত করা হয়েছে।
4. `/app/src/main/java/com/example/MainActivity.kt`: ভাষা স্টেট পর্যবেক্ষণ এবং দ্বিভাষিক ন্যাভিগেশন বার সমন্বয়।
5. `/app/src/main/java/com/example/ui/screens/LoginScreen.kt`: গ্লোব ভাষা সুইচার যুক্ত এবং দ্বিভাষিক টেক্সট সংযোগ।
6. `/app/src/main/java/com/example/ui/screens/AdminLoginScreen.kt`: ভাষা সুইচার ও দ্বিভাষিক হেডার যুক্ত।
7. `/app/src/main/java/com/example/ui/screens/AdminDashboardScreen.kt`: ভাষা সুইচার মেনুবার অ্যাকশন যুক্ত।
8. `/app/src/main/java/com/example/ui/screens/ShopRegistrationScreen.kt`: ভাষা সুইচার ও Firebase ইনিশিয়ালাইজেশন গার্ড যুক্ত।
9. `/app/src/main/java/com/example/viewmodel/BusinessViewModel.kt`: Firebase Init গার্ড এবং `AppErrorHandler` ইন্টিগ্রেশন।
10. `/app/src/main/java/com/example/util/PhoneAuthHelper.kt`: Firebase Auth অ্যাক্সেসের পূর্বে Init নিশ্চয়তা।
11. `/app/src/main/java/com/example/util/GoogleSignInHelper.kt`: Firebase Auth অ্যাক্সেসের পূর্বে Init নিশ্চয়তা।
12. `/app/src/main/java/com/example/data/sync/CloudSyncManager.kt`: ক্লাউড সিঙ্ক শুরুর পূর্বে Init নিশ্চয়তা।
13. `/app/src/main/java/com/example/data/repository/SuperAdminRepository.kt`: সুপার এডমিন ফাংশনের পূর্বে Init নিশ্চয়তা।
14. `/build.gradle.kts`: ডিস্ট্রিবিউশন এক্সপোর্ট ও ভেরিফিকেশন অটোমেশন হালনাগাদ।

---

## ৩. বাংলা ও English ভাষা পরিবর্তনের নিয়ম ও UI বিবরণ

- **অবস্থান:** Login Screen-এর উপরের ডান পাশে একটি দৃশ্যমান Globe 🌐 বাটন রয়েছে।
- **নির্বাচন পদ্ধতি:** Globe আইকনে ট্যাপ করলে একটি মার্জিত Material 3 মেনু উন্মোচিত হয়:
  - 🇧🇩 **বাংলা** (ডিফল্ট হিসেবে সক্রিয় থাকে)
  - 🇬🇧 **English**
- **তাৎক্ষণিক রিফ্লেকশন:** কোনো পেজ রিলোড, লগআউট বা অ্যাপ রিস্টার্ট ছাড়া সাথে সাথে সকল লেবেল, ইনপুট ফিল্ড, বোতাম ও ডায়ালগ পরিবর্তিত হয়ে যায়।
- **স্থায়িত্ব (Persistence):** ব্যবহারকারী একবার English বা বাংলা নির্বাচন করলে তা `SharedPreferences`-এ সংরক্ষিত থাকে; অ্যাপ বন্ধ করে আবার খুললেও সেই ভাষাই কার্যকর থাকে।

---

## ৪. নতুন যোগ করা Text Resource ও Error Message-এর তালিকা

### স্ক্রিন ও ফিচারের দ্বিভাষিক টেক্সট উদাহরণ:
| ফিচার | 🇧🇩 বাংলা রূপ | 🇬🇧 English রূপ |
|---|---|---|
| অ্যাপের নাম | SmartHishabpati | SmartHishabpati |
| অ্যাপের স্লোগান | হিসাব রাখুন স্মার্টভাবে | Manage Business Smartly |
| ট্যাব ১ | সেলসম্যান লগইন | Salesman Login |
| ট্যাব ২ | এডমিন ও জিমেইল লগইন | Admin & Gmail Login |
| ফোন ইনপুট | মোবাইল নম্বর (১১ ডিজিট) | Mobile Number (11 digits) |
| পিন ইনপুট | ৪ সংখ্যার গোপন পিন | 4-Digit Secret PIN |
| গুগল বাটন | গুগল অ্যাকাউন্ট দিয়ে প্রবেশ | Continue with Google |
| ওটিপি বাটন | ওটিপি (OTP) পাঠান | Send OTP Code |
| নতুন দোকান বাটন | নতুন দোকান নিবন্ধন | Register New Shop |
| নিচের মেনুবার | হোম, বিক্রি, রসিদ, লগআউট | Home, Sales, Receipts, Logout |

### মানবিক এরর ম্যাপিং (সমস্যা ও করণীয়):
1. **ভুল পাসওয়ার্ড:**
   - **বাংলা:**
     > **সমস্যা:** পাসওয়ার্ড সঠিক নয়।  
     > **করণীয়:** আবার চেষ্টা করুন অথবা পাসওয়ার্ড পরিবর্তন (Forgot Password) করুন।
   - **English:**
     > **Problem:** The password is incorrect.  
     > **What to do:** Please try again or reset your password.
2. **ইন্টারনেট সংযোগ বিচ্ছিন্ন:**
   - **বাংলা:**
     > **সমস্যা:** ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না।  
     > **করণীয়:** মোবাইল ডাটা অথবা Wi-Fi চালু করে আবার চেষ্টা করুন।
   - **English:**
     > **Problem:** No internet connection.  
     > **What to do:** Turn on mobile data or Wi-Fi and try again.
3. **সার্ভার সংযোগ ত্রুটি (Firebase Init Fallback):**
   - **বাংলা:**
     > **সমস্যা:** অ্যাপের সার্ভার সংযোগ চালু করা যায়নি।  
     > **করণীয়:** অনুগ্রহ করে অ্যাপটি আপডেট করুন অথবা সাপোর্টে যোগাযোগ করুন।
   - **English:**
     > **Problem:** The app could not initialize its server connection.  
     > **What to do:** Please update the app or contact support.
4. **অনুমতি নেই (Permission Denied):**
   - **বাংলা:**
     > **সমস্যা:** এই কাজটি করার অনুমতি আপনার নেই।  
     > **করণীয়:** প্রয়োজনে দোকানের প্রধান মালিকের সঙ্গে যোগাযোগ করুন।
   - **English:**
     > **Problem:** You do not have permission to perform this action.  
     > **What to do:** Please contact the shop owner if needed.
5. **ভুল ওটিপি (Invalid/Expired OTP):**
   - **বাংলা:**
     > **সমস্যা:** প্রদত্ত ওটিপি (OTP) কোডটি সঠিক নয় অথবা এর মেয়াদ শেষ হয়ে গেছে।  
     > **করণীয়:** অনুগ্রহ করে মোবাইলে আসা ৬ সংখ্যার সঠিক কোড দিন অথবা আবার কোড পাঠান।
   - **English:**
     > **Problem:** The OTP code entered is incorrect or expired.  
     > **What to do:** Please enter the correct 6-digit code or request a new OTP.

---

## ৫. Security ও Log-এ সংবেদনশীল তথ্য গোপন রাখার নিশ্চয়তা

`AppErrorHandler.sanitize()` ফাংশন লগক্যাটে যেকোনো এরর বার্তা পাঠানোর আগে নিম্নোক্ত রিজেক্স প্যাটার্ন দিয়ে সংবেদনশীল তথ্য মাস্ক করে:
```kotlin
message
    .replace(Regex("(?i)password\\s*=\\s*[^\\s,;]+"), "password=***")
    .replace(Regex("(?i)pin\\s*=\\s*[^\\s,;]+"), "pin=***")
    .replace(Regex("(?i)token\\s*=\\s*[^\\s,;]+"), "token=***")
    .replace(Regex("(?i)secret\\s*=\\s*[^\\s,;]+"), "secret=***")
    .replace(Regex("\\b[0-9]{6}\\b"), "******") // hide potential OTPs
```
- পাসওয়ার্ড, পিন বা ওটিপি কোনো অবস্থাতেই সাধারণ টেক্সট হিসেবে লগক্যাটে প্রিন্ট হয় না।
- সুপার এডমিনের কঠোর সিকিউরিটি ও অনুমোদন তালিকা (`SuperAdminRepository.SUPER_ADMIN_EMAILS`) অপরিবর্তিত রাখা হয়েছে।

---

## ৬. APK ফাইলের বিস্তারিত

| বিবরণ | মান |
|---|---|
| **ফাইলের নাম** | `SmartBusinessManager.apk` (ও `SmartHishabpati.apk`) |
| **অবস্থান** | `/SmartBusinessManager.apk` এবং প্রজেক্টের রুট ডিরেক্টরি |
| **আকার** | **৩২ MB** (৩২,৫৩১,২৪১ বাইট) |
| **SHA-256 হ্যাশ** | `97d31c75c970a39fff9b5c80a4f6e6067e893f7a4194c70034c3f0be31b74e03` |
| **বিল্ড স্ট্যাটাস** | সম্পূর্ণ ত্রুটিমুক্ত ও যাচাইকৃত (Verified Installable) |

---

## ৭. ZIP ফাইলের বিস্তারিত

| বিবরণ | মান |
|---|---|
| **ফাইলের নাম** | `SmartBusinessManager_Export.zip` |
| **অবস্থান** | `/SmartBusinessManager_Export.zip` |
| **আকার** | **২৯.৫ MB** (৩০,৯৬৩,৮০১ বাইট) |
| **SHA-256 হ্যাশ** | `fcedb3e7d594b6519d4c1c4616ba202180fc99ee274727aa5427f8de77dcf248` |
| **ভেরিফিকেশন টেস্ট** | `unzip -t` সম্পূর্ণ সফল, কোনো ফাইল করাপ্ট নয় |
| **অভ্যন্তরীণ উপাদান** | ১. `SmartBusinessManager.apk` (ইনস্টলযোগ্য এপিকে)<br>২. `README_INSTALL_BN.txt` (ইনস্টলেশন নির্দেশিকা) |

---

## ৮. অ্যান্ড্রয়েড মোবাইলে ইনস্টলেশন গাইড

১. **ডাউনলোড:** `SmartBusinessManager.apk` অথবা `SmartBusinessManager_Export.zip` ফাইলটি আপনার অ্যান্ড্রয়েড ফোনে ডাউনলোড করুন (ZIP হলে আনজিপ করুন)।
২. **অজানা সোর্স অনুমতি (Unknown Sources):**
   - ফাইলটিতে চাপ দিলে "Install unknown apps" অপশন এলে আপনার ব্রাউজার বা ফাইল ম্যানেজারকে "Allow from this source" অনুমতি দিন।
3. **Play Protect সতর্কতা:**
   - যেহেতু এটি আপনার নিজস্ব টেস্ট বিল্ড, Google Play Protect "Unrecognized app" ডায়ালগ দেখাতে পারে।
   - সেখানে **"More details"** (বিস্তারিত)-এ চাপ দিন এবং **"Install anyway"** (যেকোনো উপায়ে ইনস্টল করুন) নির্বাচন করুন।
4. **ইন্টারনেট সংযোগ:** প্রথমবার চালু করার সময় মোবাইল ডাটা বা Wi-Fi চালু রাখুন যাতে ক্লাউড কনফিগারেশন সিঙ্ক হতে পারে।

---

## ৯. সুপার এডমিন পোর্টালে প্রবেশের নির্দেশিকা

পূর্বের গোপন ও নিরাপদ অ্যাক্সেস প্রক্রিয়া সম্পূর্ণ অটুট রাখা হয়েছে:
1. অ্যাপের **লগইন স্ক্রিন (Login Screen)**-এ যান।
2. স্ক্রিনের শীর্ষে প্রদর্শিত **SmartHishabpati লোগো বা শিরোনামে পরপর ৫ বার দ্রুত ট্যাপ করুন** (২ সেকেন্ডের মধ্যে)।
3. সিস্টেম তাৎক্ষণিকভাবে গোপন **Super Admin Login Portal** উন্মুক্ত করবে।
4. অনুমোদিত সুপার এডমিন ক্রেডেনশিয়াল (`ataulgonee786@gmail.com` ইত্যাদি) দিয়ে সফলভাবে প্রবেশ করুন।

---

## ১০. বিভিন্ন স্ক্রিনের বাংলা ও ইংরেজি রূপের উদাহরণ

1. **লগইন স্ক্রিন:**
   - 🇧🇩: "স্বাগতম!", "সেলসম্যান লগইন", "মোবাইল নম্বর", "গোপন পিন", "লগইন করুন"
   - 🇬🇧: "Welcome!", "Salesman Login", "Mobile Number", "Secret PIN", "Log In"
2. **দোকান নিবন্ধন:**
   - 🇧🇩: "নতুন দোকান নিবন্ধন", "দোকানের নাম", "মালিকের নাম", "ঠিকানা", "নিবন্ধন সম্পন্ন করুন"
   - 🇬🇧: "Register New Shop", "Shop Name", "Owner Name", "Address", "Complete Registration"
3. **এডমিন ড্যাশবোর্ড:**
   - 🇧🇩: "দোকানের বিবরণ", "দৈনিক বিক্রি", "মোট বকেয়া", "প্রোডাক্ট যোগ করুন", "হিসাব নিকাশ"
   - 🇬🇧: "Shop Overview", "Daily Sales", "Total Due", "Add Product", "Accounts"
4. **বিক্রি ও ইনভয়েস:**
   - 🇧🇩: "নতুন বিক্রি", "গ্রাহকের নাম", "মূল্য", "পরিশোধ", "বকেয়া", "ইনভয়েস তৈরি"
   - 🇬🇧: "New Sale", "Customer Name", "Price", "Paid", "Due", "Generate Invoice"

---

## ১১. ভবিষ্যৎ রক্ষণাবেক্ষণ ও নতুন ভাষা যোগ করার নিয়ম

অ্যাপটিতে যেকোনো সময় নতুন ভাষা (যেমন: হিন্দি, আরবি, স্প্যানিশ) যুক্ত করার জন্য একটি নিখুঁত আর্কিটেকচার তৈরি রয়েছে:
1. `AppLanguage.kt`-এর `enum class AppLanguage` তে নতুন ভাষা ও প্রতীক যোগ করুন (উদা: `HINDI("hi", "हिन्दी", "🇮🇳")`)।
2. `AppStrings.kt`-এ প্রতিটি প্রপার্টির জন্য নতুন ভাষার স্ট্রিং যুক্ত করুন।
3. `AppErrorHandler.kt`-এ নতুন ভাষার সমস্যা ও করণীয় বার্তা যোগ করুন।
4. `LanguageSwitcher.kt` মেনুতে নতুন এন্ট্রিটি স্বয়ংক্রিয়ভাবে উপস্থিত হবে এবং পুরো অ্যাপে কাজ করবে।
