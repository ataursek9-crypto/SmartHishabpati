# Smart Business Manager — Super Admin Crash Fix & Installation Guide

## ১. সমাধান সারাংশ (Fix Summary)
- **সমস্যার মূল কারণ (Root Cause):** `SuperAdminViewModel` ক্লাসে JVM বাইটকোডে একক প্যারামিটার কনস্ট্রাক্টর `SuperAdminViewModel(Application)` উপস্থিত না থাকায় `AndroidViewModelFactory` জাভা রিফ্লেকশন দিয়ে ইনস্ট্যান্টশিয়েট করতে গিয়ে `NoSuchMethodException` ও `RuntimeException` তৈরি করত। এর ফলে Xiaomi/MIUI ফোনে "Smart Business Manager keeps stopping" ক্র্যাশ হতো।
- **সমাধান:** 
  1. `SuperAdminViewModel`-এ `@JvmOverloads` এবং `provideFactory(application)` যোগ করে সরাসরি কনস্ট্রাক্টর রেজিস্টার করা হয়েছে।
  2. `MainActivity.kt`-এ `superAdminViewModel` লাইফসাইকেল বাইন্ডিং এবং রুট লেভেল সিকিউরিটি গার্ড যুক্ত করা হয়েছে।
  3. `SuperAdminDashboardScreen.kt`-এ সরাসরি সিকিউরিটি কার্ড গার্ড দেওয়া হয়েছে, যাতে কোনো অননুমোদিত ইউজার সরাসরি প্রবেশ করতে না পারে।

---

## ২. ফাইল ও সরাসরি ডাউনলোড লিঙ্ক (Verified Download Files)
| ফাইল | সাইজ | লোকেশন |
|---|---|---|
| **Smart-Business-Manager-debug.apk** | ৩২ MB | `APK/Smart-Business-Manager-debug.apk` |
| **Smart_Business_Manager_v1.0_Source_and_APK.zip** | ৬০ MB | `Smart_Business_Manager_v1.0_Source_and_APK.zip` |

---

## ৩. ইনস্টলেশন নির্দেশিকা (Android Phone Installation)
১. `APK/Smart-Business-Manager-debug.apk` ফাইলটি ফোনে নিন।  
২. ফাইলে ট্যাপ করে Install নির্বাচন করুন।  
৩. "Install unknown apps" অনুমতি চাইলে Settings থেকে Enable করে দিন।  
৪. অ্যাপ ওপেন করে Google Sign-In সম্পন্ন করুন।  
৫. এডমিন ড্যাশবোর্ডের “প্ল্যাটফর্ম সুপার এডমিন প্যানেল” বাটনে ট্যাপ করলে কোনো ক্র্যাশ ছাড়া সম্পূর্ণ সুপার এডমিন কনসোল চালু হবে।
