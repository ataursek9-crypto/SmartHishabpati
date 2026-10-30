# 📱 Smart Business Manager — APK & Project Download Guide

## ১. কেন "Export project as ZIP" অপশনে APK ছিল না?

Google AI Studio-এর শীর্ষ মেনুর **"Export project as ZIP"** ফিচারটি মূলত গিট ট্র্যাকিং ভিত্তিক সোর্স কোড ডাউনলোড করে। যেহেতু প্রতিটি স্ট্যান্ডার্ড অ্যান্ড্রয়েড প্রজেক্টের `.gitignore`-এ `/build` ডিরেক্টরি অন্তর্ভুক্ত থাকে, তাই স্বয়ংক্রিয় এক্সপোর্টে কম্পাইলকৃত `app/build/` ফোল্ডার এবং ভেতরের APK ফাইলটি স্বয়ংক্রিয়ভাবে বাদ পড়ে এবং ZIP ফাইলের সাইজ মাত্র ১৪ MB হয়েছিল।

---

## ২. বাস্তব বিল্ড ও APK ফাইল ভেরিফিকেশন রিপোর্ট

আমরা Gradle বিল্ড সিস্টেম দিয়ে সম্পূর্ণ নতুন APK তৈরি ও একাধিক স্তরে যাচাই করেছি:

| আইটেম | মান / স্থিতি |
|---|---|
| **বিল্ড স্ট্যাটাস** | ✅ **BUILD SUCCESSFUL** (`:app:assembleDebug`) |
| **অ্যাপ্লিকেশন আইডি** | `com.aistudio.smartbiz.mgr` |
| **ভার্সন** | `1.0` (versionCode: 1) |
| **APK ফাইলের নাম** | `SmartBusinessManager.apk` |
| **সরাসরি APK ফাইলের পাথ** | `/SmartBusinessManager.apk` এবং `/APK/SmartBusinessManager.apk` |
| **আসল APK ফাইলের আকার** | **৩৩,৫৩০,৩২৭ বাইট (~৩১.৯৮ MB)** |
| **ZIP ফাইলের নাম** | `SmartBusinessManager_Project.zip` |
| **ZIP ফাইলের মোট আকার** | **~৬৭ MB** |
| **ZIP-এর ভেতর APK উপস্থিতি** | ✅ যাচাইকৃত (`APK/SmartBusinessManager.apk`, সাইজ: ৩১.৯৮ MB) |

---

## ৩. ফাইল ডাউনলোডের দুটি সহজ পদ্ধতি

### পদ্ধতি ১: সরাসরি APK ডাউনলোড (সবচেয়ে সহজ ও দ্রুত)
1. Google AI Studio স্ক্রিনের বাম পাশের **File Explorer** (ফাইল তালিকা)-এ যান।
2. রুট ডিরেক্টরিতে **`SmartBusinessManager.apk`** ফাইলটি দেখতে পাবেন।
3. ফাইলের ডান পাশে থাকা **৩-ডট (⋮)** মেনুতে ক্লিক করুন।
4. **"Download"** অপশন নির্বাচন করুন।
5. সম্পূর্ণ ৩১.৯৮ MB আকারের আসল ইনস্টলেশন ফাইলটি সাথে সাথে আপনার কম্পিউটারে ডাউনলোড হয়ে যাবে।

### পদ্ধতি ২: সম্পূর্ণ প্রজেক্ট ও APK সহ ZIP ডাউনলোড
1. বাম পাশের ফাইল এক্সপ্লোরারে **`SmartBusinessManager_Project.zip`** ফাইলটির ওপর যান (সাইজ: ~৬৭ MB)।
2. পাশে থাকা **৩-ডট (⋮)** মেনুতে ক্লিক করে **"Download"** দিন।
3. এই ZIP ফাইলটি আনজিপ করলে ভেতরে পাবেন:
   - `APK/SmartBusinessManager.apk` (ইনস্টলযোগ্য আসল APK)
   - `SmartBusinessManager_Project/` (সম্পূর্ণ সোর্স কোড)
   - `README_BN.txt` (ইনস্টলেশন ও ফায়ারবেস গাইড)

---

## ৪. Firebase Console সাইনিং কনফিগারেশন

স্মার্টফোনে Google Sign-In সফলভাবে সম্পন্ন করতে Firebase Console-এ নিচের সাইনিং তথ্য নিশ্চিত করুন:
- **Package Name:** `com.aistudio.smartbiz.mgr`
- **SHA-1:** `78:3B:FF:37:FE:18:37:6B:71:AF:DF:82:F3:2F:F1:A0:86:3D:C6:2C`
- **SHA-256:** `A9:31:55:5F:E0:E6:CD:58:E2:D5:75:D3:31:E7:85:7B:AA:DF:B3:14:90:A2:D4:96:D8:9D:D1:45:CD:70:73:D8`
- **Web Client ID:** `484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com`
