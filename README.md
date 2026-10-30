# Smart Business Manager — Android Studio Project

স্মার্ট বিজনেস ম্যানেজার (Smart Business Manager) একটি আধুনিক, সম্পূর্ণ অফলাইন ও স্থানীয় ডাটাবেস-সমৃদ্ধ সম্পূর্ণ Android অ্যাপ্লিকেশন। এটি খুচরা ও পাইকারি ব্যবসায়ের জন্য কেনাবেচা, ইনভয়েস/রসিদ তৈরি, স্টক/ইনভেন্টরি ট্র্যাকিং, কাস্টমার বাকি ও কোম্পানি/সাপ্লায়ার বাকি হিসাব ব্যবস্থাপনা, স্মার্ট রিমাইন্ডার এবং অ্যাডমিন-টু-সেলসম্যান অ্যালার্ট ও ভয়েস নির্দেশনা সিস্টেমসহ স্বয়ংসম্পূর্ণ।

---

## 📁 প্রোজেক্টের মূল বৈশিষ্ট্য ও মডিউল

1. **প্রোডাক্ট ও ইনভেন্টরি ম্যানেজমেন্ট**:
   - বারকোড স্ক্যানার, ক্যামেরা ও ফটো পিকার দিয়ে প্রোডাক্ট যোগ।
   - Gemini AI ইমেজ জেনারেশন ও উইকিমিডিয়া ওপেন ইমেজ সার্চ সুবিধা।
   - স্টক ট্র্যাকিং, কম স্টক সতর্কতা (Low Stock Alert)।

2. **সেলস ও ইনভয়েস জেনারেশন**:
   - সহজ ও দ্রুত বিক্রয় রসিদ তৈরি (POS Style)।
   - থার্মাল ব্লুটুথ প্রিন্ট প্রিভিউ এবং ডিজিটাল ইনভয়েস শেয়ারিং।

3. **বাকি ও হিসাব খাতা (Customer & Supplier Ledger)**:
   - কাস্টমার বাকি এবং কোম্পানি/মহাজন বাকি খাতা।
   - পেমেন্ট কালেকশন ও আংশিক পরিশোধ এন্ট্রি।

4. **স্মার্ট রিমাইন্ডার (Smart Reminder System)**:
   - কাস্টমার ও মহাজন বাকি আদায়ের স্বয়ংক্রিয় শিডিউল ও অ্যালার্ম নোটিফিকেশন।
   - বাংলা ভয়েস প্রম্পট (TTS) সহ সতর্কতা।

5. **অ্যাডমিন-টু-সেলসম্যান অ্যালার্ট ও ভয়েস সিস্টেম (Admin-to-Salesman Alert & Voice System)**:
   - অ্যাডমিন ড্যাশবোর্ড থেকে নির্দিষ্ট বা সকল সেলসম্যানকে সাধারণ বা জরুরি নির্দেশ প্রেরণ।
   - তাৎক্ষণিক প্রেরণ অথবা নির্দিষ্ট সময়সূচীতে (Scheduled) প্রেরণ।
   - বাংলা ভয়েস নির্দেশনা (Text-to-Speech) ও নোটিফিকেশন অ্যালার্ট।
   - সেলসম্যান ড্যাশবোর্ড থেকে নির্দেশ গ্রহণ, "বুঝেছি" ও "কাজ শেষ" স্ট্যাটাস নিশ্চিতকরণ।
   - অ্যাডমিন প্যানেলে রিয়েল-টাইম স্ট্যাটাস ও ইতিহাস ট্র্যাকিং।

6. **অফলাইন ব্যাকআপ ও ডেটা প্রটেকশন**:
   - JSON / SQLite লোকাল ব্যাকআপ এক্সপোর্ট ও রিস্টোর।

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.x
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Database**: Room Database (SQLite) with TypeConverters & Safe SQLite Migrations
- **Architecture**: MVVM (Model-View-ViewModel) + Kotlin Coroutines & StateFlow
- **Image Handling**: Coil + Local File System Storage + Android Photo Picker + Camera
- **Voice / Audio**: Android Text-To-Speech (TTS) Engine for Bengali & English
- **Build System**: Gradle (Kotlin DSL `.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 🚀 Android Studio-তে কীভাবে খুলবেন ও রান করবেন

### ধাপ ১: আনজিপ (Unzip) করুন
`Smart-Business-Manager-Source.zip` ফাইলটি এক্সট্র্যাক্ট করুন।
ফোল্ডার কাঠামো:
```text
Smart-Business-Manager/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/...
│       │   └── res/...
│       └── test/...
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .env.example
├── .gitignore
├── metadata.json
└── README.md
```

### ধাপ ২: Android Studio-তে Open করুন
1. **Android Studio** (Ladybug / Koala / Hedgehog বা নতুন সংস্করণ) চালু করুন।
2. `File` -> `Open...` ক্লিক করে আনজিপ করা ফোল্ডারটি নির্বাচন করুন।
3. Gradle Sync সম্পন্ন হওয়া পর্যন্ত অপেক্ষা করুন।

### ধাপ ৩: Build ও Run
- টার্মিনালে রান করুন:
  ```bash
  ./gradlew assembleDebug
  ```
  (Windows-এ `gradlew.bat assembleDebug`)
- অথবা Android Studio-র উপরের টুলবারে **Run 'app'** (Green Play Button ▶) চাপুন।

---

## 📦 প্রস্তুতকৃত APK ফাইল
- **APK ফাইলের নাম**: `Smart-Business-Manager-Debug.apk`
- **বিল্ড পাথ**: `app/build/outputs/apk/debug/app-debug.apk`
- **Application ID**: `com.aistudio.smartbiz.mgr`
- **Version Code**: `1` (Version 1.0)
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 36 (Android 15+)
- এই ফাইলটি যেকোনো অ্যান্ড্রয়েড ডিভাইসে সরাসরি সাইডলোড বা ইনস্টল করা যায়।

---

## 🔒 ডেটা নিরাপত্তা ও গোপনীয়তা
- সোর্স জিপ ফাইলে কোনো ব্যক্তিগত API কী, পাসওয়ার্ড বা সাইনিং কি-স্টোর অন্তর্ভুক্ত করা হয়নি।
- বিদ্যমান সব বিক্রয় ডাটা, কাস্টমার খাতা ও অ্যালার্ট সিস্টেম অক্ষত এবং সম্পূর্ণরূপে কার্যকর।

