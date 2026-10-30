# Smart Business Manager — Google Sign-In Fix & APK Release Report

## ১. Google Sign-In সমস্যার মূল কারণ ও সমাধান

### সমস্যার লক্ষণ ও ত্রুটি:
- ত্রুটি: `GetCredential CancellationException: activity is cancelled by the user`
- লক্ষণ: Google Sign-In বাটনে চাপ দিলে উইন্ডো না দেখিয়েই সরাসরি বন্ধ হয়ে যেত।

### মূল কারণসমূহ:
1. **Firebase Console-এ সাইনিং SHA-1 অনুপস্থিতি:** বর্তমান APK-টি যে `debug.keystore` দিয়ে সাইন করা হয়েছে, তার প্রকৃত SHA-1 হলো `7A:2A:12:6E:35:7C:64:FD:04:1C:AF:42:F4:47:BE:F6:69:6E:56:EB`। ব্যবহারকারী এর আগে যে SHA-1 টি দেখেছিলেন (`72:FA:3A:98:28:0A:44:8B:35:B9:9D:19:3B:93:8B:39:35:F8:A9:E9`), তা এই কি-এর সাথে অমিল ছিল। ফায়ারবেসে এই কি-এর অনুমোদন না থাকায় Google Play Services ডায়ালগ খোলার পূর্বেই রিকোয়েস্টটি স্বয়ংক্রিয়ভাবে ক্যানসেল (Code 16 / Auto-Dismiss) করে দিত।
2. **Auto-Cancellation বনাম User Manual Cancellation:** পূর্বে `GetCredentialCancellationException` ঘটলে কোড ধরে নিত ব্যবহারকারী নিজে ডায়ালগ কেটে দিয়েছেন, ফলে স্ক্রিনে কোনো ত্রুটি বার্তা আসত না। এখন ১২০০ মিলিসেকেন্ডের নিচে স্বয়ংক্রিয় ক্যানসেলেশন শনাক্ত করে বিস্তারিত কারণ ও সমাধানের গাইড দেখানো হয়।
3. **google-services.json সক্রিয়করণ:** প্রজেক্টের রুট থেকে সঠিক `app/google-services.json` ফাইলে কনফিগারেশন স্থানান্তর করা হয়েছে, যার ফলে Firebase Auth এবং Google Client ID রিসোর্স স্বয়ংক্রিয়ভাবে তৈরি হয়েছে।

---

## ২. APK সাইনিং সার্টিফিকেট বিবরণ

| প্রোপার্টি | মান |
|---|---|
| **প্যাকেজ নেম** | `com.aistudio.smartbiz.mgr` |
| **ভার্সন** | `1.0` (versionCode: 1) |
| **প্রকৃত সাইনিং SHA-1** | `7A:2A:12:6E:35:7C:64:FD:04:1C:AF:42:F4:47:BE:F6:69:6E:56:EB` |
| **প্রকৃত সাইনিং SHA-256** | `8D:63:67:62:87:C7:A7:AC:0D:C0:F0:31:86:C8:C1:DF:BC:55:3C:B7:E1:55:6B:CE:60:8C:26:92:42:73:3A:58` |
| **Web Client ID** | `484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com` |
| **Firebase Project ID** | `smart-business-manager-71879` |

---

## ৩. তৈরি হওয়া প্যাকেজ ও ডাউনলোড অপশন

### ১. রিলিজ ZIP প্যাকেজ:
- **ফাইলের নাম:** `Smart_Business_Manager_Release.zip` (সাইজ: ~29 MB)
- **ZIP-এর ভেতরের কনটেন্ট (যাচাইকৃত):**
  - `app-debug.apk` (আকার: **32,756,172 bytes** / ~31.24 MB)
  - `README.txt` (ইনস্টলেশন ও ফায়ারবেস কনফিগারেশন নির্দেশিকা)
  - `BUILD_REPORT.txt` (সম্পূর্ণ টেস্ট ও বিল্ড ভেরিফিকেশন রিপোর্ট)

### ২. ডাউনলোড করার পদ্ধতি:
1. **AI Studio ফাইল এক্সপ্লোরার থেকে:** বাম পাশের File Explorer-এ যান। সেখানে রুট ডিরেক্টরিতে সরাসরি দেখতে পাবেন:
   - `Smart_Business_Manager_Release.zip`
   - `app-debug.apk`
   - `Smart_Business_Manager.apk`
   ফাইলের পাশে থাকা ৩-ডট (⋮) মেনুতে ক্লিক করে **"Download"** সিলেক্ট করলেই সাথে সাথে ফাইলটি আপনার কম্পিউটারে সেভ হবে।
2. **AI Studio "Export as ZIP" অপশন থেকে:** স্ক্রিনের উপরের ডানদিকের Settings / Three Dots (⋮) মেনু থেকে **"Export project as ZIP"** নির্বাচন করুন।
