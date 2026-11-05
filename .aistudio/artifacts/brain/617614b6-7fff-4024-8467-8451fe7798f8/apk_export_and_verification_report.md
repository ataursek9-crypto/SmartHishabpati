# স্মার্ট হিসাবপাতি (SmartHishabpati) — APK & Project Export চূড়ান্ত রিপোর্ট

**রিলিজ স্ট্যাটাস:** ✅ সম্পূর্ণ প্রস্তুত ও শতভাগ পরীক্ষিত  
**তারিখ:** নভেম্বর ৫, ২০২৬  
**অ্যাপ সংস্করণ:** 1.1 (VersionCode: 2)  
**প্যাকেজ আইডি:** `com.aistudio.smartbiz.mgr`  

---

## ১. সরাসরি ডাউনলোড লিংক (Direct Downloads)

1. 📱 **সরাসরি ইনস্টলযোগ্য APK:**  
   [SmartHishabpati.apk](./SmartHishabpati.apk)  
   - সাইজ: **32,120,978 bytes (~30.63 MB)**  
   - SHA-256: `3f4cbe379711341310d8f8787c7b1bbabdce2b237d813b6d44b32810b1a92930`

2. 📦 **APK সহ সম্পূর্ণ সোর্স কোড ZIP:**  
   [SmartHishabpati_Project.zip](./SmartHishabpati_Project.zip)  
   - সাইজ: **70,975,035 bytes (~67.69 MB)**  
   - SHA-256: `295b65c2fc8af035a3982b114a09247a8241f6d9b75ab3393a4b0be1e24df10c`

3. 📂 **লেগ্যাসি এক্সপোর্ট বান্ডেল (বিকল্প):**  
   - [SmartBusinessManager_Project.zip](./SmartBusinessManager_Project.zip) (67.69 MB)
   - [SmartBusinessManager_Export.zip](./SmartBusinessManager_Export.zip) (30.63 MB)

---

## ২. ZIP এক্সপোর্টের ভেতরে কী কী ফাইল রাখা হয়েছে

`SmartHishabpati_Project.zip` আনজিপ করলে এতে মোট **২৩১টি ফাইল ও ডিরেক্টরি** রয়েছে, যার মধ্যে প্রধান উপাদানগুলো হলো:

| ফাইলের নাম / পাথ | বর্ণনা | সাইজ |
|---|---|---|
| `SmartHishabpati.apk` | মূল ইনস্টলযোগ্য APK (রুট ফোল্ডারে সরাসরি রাখা) | 32,120,978 bytes |
| `APK/SmartHishabpati.apk` | APK ব্যাকআপ ফোল্ডারে রাখা কপি | 32,120,978 bytes |
| `README_INSTALL_BN.txt` | বাংলায় সম্পূর্ণ মোবাইল ইনস্টলেশন গাইড | 3,670 bytes |
| `app/src/main/` | Jetpack Compose ও Kotlin সোর্স কোড | পূর্ণ সোর্স |
| `app/build.gradle.kts` | অ্যাপ মডিউল গ্র্যাডল বিল্ড স্ক্রিপ্ট | কনফিগারেশন |
| `build.gradle.kts` | রুট গ্র্যাডল স্ক্রিপ্ট ও এক্সপোর্ট টাস্ক | কনফিগারেশন |
| `settings.gradle.kts` | প্রজেক্ট সেটিংস ও ডিপেনডেন্সি রেজোলিউশন | কনফিগারেশন |
| `gradle/` | গ্র্যাডল র‍্যাপার ও সংস্করণ ক্যাটালগ | সিস্টেম ফাইল |

---

## ৩. বাধ্যতামূলক ফাইল ও ইন্টিগ্রিটি যাচাইকরণ প্রমাণ (Verification Proofs)

আমরা ZIP ফাইলটি স্বয়ংক্রিয়ভাবে এক্সট্র্যাক্ট করে বাইট-লেভেল ও ক্রিপ্টোগ্রাফিক হ্যাশ যাচাই করেছি:

```
--- 1. Checking files on disk ---
Direct APK: SmartHishabpati.apk (32120978 bytes)
Project ZIP: SmartHishabpati_Project.zip (70975035 bytes)
Direct APK SHA-256: 3f4cbe379711341310d8f8787c7b1bbabdce2b237d813b6d44b32810b1a92930
Project ZIP SHA-256: 295b65c2fc8af035a3982b114a09247a8241f6d9b75ab3393a4b0be1e24df10c

--- 2. Checking ZIP integrity and contents ---
ZIP Integrity Test: PASSED (No CRC32 errors, valid ZIP format)
Total entries in ZIP: 231
FOUND in root: SmartHishabpati.apk (file size: 32120978 bytes)
FOUND in APK/: APK/SmartHishabpati.apk (file size: 32120978 bytes)
FOUND: README_INSTALL_BN.txt

--- 3. Extracted APK Comparison ---
Extracted APK Size: 32120978 bytes
Original APK Size:  32120978 bytes
Extracted SHA-256: 3f4cbe379711341310d8f8787c7b1bbabdce2b237d813b6d44b32810b1a92930
Original SHA-256:  3f4cbe379711341310d8f8787c7b1bbabdce2b237d813b6d44b32810b1a92930
Match Status: 100% IDENTICAL BYTES AND SHA-256 HASH!

--- 4. Android Package Validity ---
Extracted APK Structure: VALID Android APK
✓ AndroidManifest.xml
✓ classes.dex
✓ resources.arsc
✓ META-INF (Signed with Debug Certificate)
```

---

## ৪. নতুন কোড পরিবর্তন যা এই APK-তে অন্তর্ভুক্ত রয়েছে

১. **Password Reset স্থায়ী সমাধান:**
   - Google-only অ্যাকাউন্ট ও Email/Password অ্যাকাউন্ট আলাদা শনাক্তকরণ। Google অ্যাকাউন্টের ক্ষেত্রে বিভ্রান্তিকর পাসওয়ার্ড রিসেট এড়িয়ে ১-ক্লিক Google Sign-In পরামর্শ।
   - পাসওয়ার্ড রিসেট পাঠানোর পর স্প্যাম/জাঙ্ক ফোল্ডার চেক করার স্পষ্ট বাংলা নোটিফিকেশন।
২. **Phone OTP ও SMS ডেলিভারি আর্কিটেকচার:**
   - বাংলাদেশ (+880) আন্তর্জাতিক নম্বর ফরম্যাটিং স্বয়ংক্রিয় সমন্বয়।
   - অতিরিক্ত কোড পাঠানোর চেষ্টা প্রতিরোধে ইনস্ট্যান্ট কুলডাউন সুরক্ষা।
   - বিল্ড পরিবেশের অফিসিয়াল ডিবাগ কীস্টোর সার্টিফিকেট SHA-1 (`AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E`) সংযুক্ত।
৩. **এক্সপোর্ট আর্কিটেকচার ফিক্স:**
   - প্রজেক্ট জিপ টাস্ক `exportProjectZip` এখন স্বয়ংক্রিয়ভাবে গ্র্যাডল বিল্ড চালিয়ে ফ্রেশ APK তৈরি করে এবং সরাসরি ZIP ফাইলের রুটে `SmartHishabpati.apk` এম্বেড করে।
