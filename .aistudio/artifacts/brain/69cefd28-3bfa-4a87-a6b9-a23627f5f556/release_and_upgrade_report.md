# Smart Business Manager — Login UI Redesign & Google Sign-In Fix Report

## ১. সারসংক্ষেপ
- **Login Screen UI Redesign**: আধুনিক, পরিচ্ছন্ন ও পেশাদার নীল-সাদা (PrimaryNavy + White) ম্যাটেরিয়াল ৩ ডিজাইন বাস্তবায়ন করা হয়েছে। ছোট মোবাইল স্ক্রিনে ব্যবহারের সুবিধার্থে সম্পূর্ণ স্ক্রিন স্ক্রোলযোগ্য এবং কোনো বাটন বা ইনপুট কেটে যাওয়ার ঝুঁকি নেই।
- **Google Sign-In Root Cause সমাধান**: বাস্তব APK-তে গুগল সাইন-ইন ব্যর্থ হওয়ার মূল কারণগুলো চিহ্নিত করে কোড এবং কনফিগারেশনে স্থায়ী সমাধান করা হয়েছে।
- **পূর্ববর্তী সমস্ত সিস্টেম অক্ষত**: লোগোতে গোপন ৫-ট্যাপ সুপার অ্যাডমিন পোর্টাল, মোবাইল নম্বর ও পিন লগইন, `ataurgonee4@gmail.com` ও `01700000000`-এর ম্যাপিং, মাল্টি-টেন্যান্ট ডেটাবেস এবং ফায়ারবেস সিকিউরিটি রুলস সম্পূর্ণরূপে অপরিবর্তিত ও সুরক্ষিত রাখা হয়েছে।

---

## ২. কোন কোন ফাইল পরিবর্তন করা হয়েছে এবং কেন

1. **`app/src/main/java/com/example/ui/screens/LoginScreen.kt`**:
   - অ্যাপ লোগো (৫-ট্যাপ গোপন ট্রিগার সহ) ও শিরোনাম সহ নীল-সাদা ব্র্যান্ডিং হেডার।
   - বাংলা শিরোনাম: "আপনার অ্যাকাউন্টে প্রবেশ করুন" এবং উপশিরোনাম।
   - মোবাইল নম্বর ও পিন ইনপুট (আই বাটন সহ দেখানো/লুকানো)।
   - "এই ডিভাইসে নম্বরটি মনে রাখুন" অপশন (যা পিন কখনও সংরক্ষণ করে না)।
   - নীল "প্রবেশ করুন" বাটন (লোডিং ও ডিজেবল্ড স্টেট সহ)।
   - "নতুন দোকান রেজিস্ট্রেশন — বিনামূল্যে" বাটন।
   - "Google একাউন্ট দিয়ে সাইন ইন" বাটন এবং এর ঠিক নিচে পরিষ্কার স্ট্যাটাস/এরর মেসেজ কন্টেইনার।
   - গুগল সাইন-ইন সহায়তায় এক ক্লিকে সাইনিং ফিঙ্গারপ্রিন্ট কপি করার ডায়ালগ।

2. **`app/src/main/java/com/example/util/GoogleSignInHelper.kt`**:
   - `SigningInfo`-এর Android 9+ বাগ সংশোধন (একক সাইনারের ক্ষেত্রে `apkContentsSigners` নাল থাকে, `signingCertificateHistory` ব্যবহার করা আবশ্যক)।
   - কোডের ফলব্যাক ফিঙ্গারপ্রিন্ট পরিবর্তন করে প্রকৃত APK সাইনিং কি-র সাথে মিল রাখা হয়েছে।

3. **`app/google-services.json`**:
   - বর্তমান APK-এর সাইনিং ফিঙ্গারপ্রিন্ট হ্যাস (`fe5198ae71d43355e9b2428b08444ac7e5e6d8a2`) `oauth_client` অ্যারেতে যুক্ত করা হয়েছে।

---

## ৩. Google Sign-In-এর প্রকৃত Root Cause এবং সমাধান

| সমস্যা / Root Cause | কেন হয়েছিল | কী সমাধান করা হয়েছে |
| :--- | :--- | :--- |
| **১. ভুল SHA-1 ফলব্যাক** | পূর্বে `GoogleSignInHelper.kt`-এ ভুয়া হার্ডকোডেড SHA-1 (`6D:B1:...`) দেওয়া ছিল। এছাড়া পূর্বের রিপোর্টে অন্য একটি ফিঙ্গারপ্রিন্ট উল্লেখ ছিল। | `apksigner` ও `keytool` দিয়ে APK-এর প্রকৃত সাইনিং SHA-1 (`FE:51:98:AE:71:D4:33:55:E9:B2:42:8B:08:44:4A:C7:E5:E6:D8:A2`) এবং SHA-256 নিশ্চিত করে কোড ও সহায়তায় যুক্ত করা হয়েছে। |
| **২. SigningInfo সার্টিফিকেট নিষ্কাশন ত্রুটি** | Android Pie (API 28+) থেকে `hasMultipleSigners() == false` হলে `apkContentsSigners` নাল রিটার্ন করে। ফলে অ্যাপ আসল সার্টিফিকেট পড়তে না পেরে ভুল ফলব্যাকে চলে যেত। | কোডে `signingCertificateHistory` চেক নিশ্চিত করা হয়েছে যাতে রানটাইমে সঠিক শংসাপত্র পাওয়া যায়। |
| **৩. google-services.json-এ ক্লায়েন্ট অমিল** | `app/google-services.json`-এর `oauth_client`-এ বর্তমান debug.keystore-এর সাইনিং হ্যাশ অন্তর্ভুক্ত ছিল না। | বর্তমান APK সাইনিং হ্যাশ যুক্ত করা হয়েছে। |

---

## ৪. Firebase Console চেকলিস্ট

গুগল প্লে সার্ভিসের মাধ্যমে রিয়েল ফোনে সাইন-ইন কার্যকর করতে Firebase Console-এ নিচের তথ্যটি থাকা নিশ্চিত করুন:

- **Package Name**: `com.aistudio.smartbiz.mgr`
- **Firebase Project**: `smart-business-manager-71879`
- **OAuth Web Client ID**: `484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com`
- **প্রকৃত Debug Keystore SHA-1**: `FE:51:98:AE:71:D4:33:55:E9:B2:42:8B:08:44:4A:C7:E5:E6:D8:A2`
- **প্রকৃত Debug Keystore SHA-256**: `C8:EC:1C:7C:80:08:69:33:D4:79:00:1B:49:46:7C:3E:84:2F:6E:AE:8E:68:E6:76:F2:03:62:2D:2E:38:EB:5F`

*(অ্যাপের লগইন স্ক্রিনের "Google Sign-In সহায়তা ও কনফিগারেশন" বাটনে ট্যাপ করে যেকোনো সময় এই তথ্যগুলো এক ক্লিকে ক্লিপবোর্ডে কপি করা যাবে)*

---

## ৫. টেস্ট ও বিল্ড রেজাল্ট
- **Unit & Robolectric Tests**: ১৪টি টেস্ট ক্লাস ও ১৪৬+ টেস্ট সফলভাবে উত্তীর্ণ (`BUILD SUCCESSFUL in 50s`)।
- **APK Verification**: `apksigner verify` দ্বারা APK ভেরিফায়েড।
- **রিলিজ ফাইলসমূহ**:
  - Direct APK: `/app/applet/SmartBusinessManager.apk` (~৩১ MB)
  - APK Folder: `/app/applet/APK/SmartBusinessManager.apk`
  - সম্পূর্ণ Android Studio প্রজেক্ট ZIP: `/app/applet/SmartBusinessManager_Project.zip` (~৬৫ MB)
