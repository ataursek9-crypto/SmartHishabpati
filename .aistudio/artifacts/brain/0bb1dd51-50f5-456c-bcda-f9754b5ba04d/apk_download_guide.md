# 📱 Smart Business Manager — রিলিজ APK ও ZIP ডাউনলোড নির্দেশিকা

আপনার অনুরোধ অনুযায়ী ফাইল সিস্টেমের রুট ডিরেক্টরি, APK ও ZIP ফাইলের উপস্থিতি, আকার, SHA-256 চেকসাম এবং সরাসরি জিপের রুটে APK রেখে **`Smart_Business_Manager_Release.zip`** প্রস্তুত ও যাচাই করা হয়েছে।

---

## 📊 ১. ফাইল সিস্টেম ও APK-এর বাস্তব ফলাফল

- **প্রজেক্ট রুট ডিরেক্টরি:** `/app/applet`
- **আসল APK পাথ:** `/app/applet/app/build/outputs/apk/debug/app-debug.apk`
- **APK ফাইলের ধরন (file type):** `Zip archive data, at least v0.0 to extract, compression method=deflate` (Android Package)
- **APK ফাইলের আকার (bytes):** `33,574,499 bytes` (~**32.02 MB** / `ls -lh`: **33M**)
- **APK SHA-256 Checksum:**
  ```
  7037ba2b168e07a69851063238fde1c62b47d7f0e10ea104e635138b08f5669d
  ```

---

## 📦 ২. নতুন `Smart_Business_Manager_Release.zip` বিবরণ

এই নতুন ZIP ফাইলের রুটে সরাসরি আসল APK রাখা হয়েছে (কোনো নেস্টেড জিপ ছাড়াই):

- **ZIP পাথ:** `/app/applet/Smart_Business_Manager_Release.zip`
- **ZIP ফাইলের আকার:** `30,401,985 bytes` (~**28.99 MB** / `ls -lh`: **29M**)
- **ZIP SHA-256 Checksum:**
  ```
  695fe74e254e432f6829ec4e7fb5fbaf7428b1a957680004a35ceda9dac92d64
  ```

### `unzip -l` দ্বারা যাচাইকৃত ফাইলের তালিকা:
```text
  Length      Date    Time    Name
---------  ---------- -----   ----
 33574499  2026-10-28 07:05   app-debug.apk
     2245  2026-10-28 07:05   README_INSTALL_BN.txt
      316  2026-10-28 07:05   BUILD_INFO.txt
---------                     -------
 33577060                     3 files
```

### চেকসাম মিল যাচাই (Byte-for-byte Match):
- মূল APK: `7037ba2b168e07a69851063238fde1c62b47d7f0e10ea104e635138b08f5669d`
- ZIP থেকে Extract করা APK: `7037ba2b168e07a69851063238fde1c62b47d7f0e10ea104e635138b08f5669d`
- **যাচাই ফলাফল:** `EXACT MATCH (হুবহু একই ফাইল)`

---

## 🔍 ৩. কেন আগের Export ZIP-এ APK খুঁজে পাওয়া যায়নি?

1. **AI Studio Export Project-এর ফিল্টার:** যখন Google AI Studio-এর সেটিংস থেকে "Export Project" বা "Download ZIP" করা হয়, তখন প্ল্যাটফর্মটি সোর্স কোড এক্সপোর্ট করে এবং ডিফল্টভাবে `.gitignore`-এ থাকা ফোল্ডার যেমন `build/` (যেখানে `app/build/outputs/apk/debug/app-debug.apk` তৈরি হয়) বাদ দিয়ে দেয়।
2. **নেস্টেড জিপ বিভ্রান্তি:** আগের প্যাকেজটি ছিল `Smart_Business_Manager_Test_APK.zip`। যখন এক্সপোর্ট জিপ (৪৪ MB) নামানো হয়েছিল, উইন্ডোজ বা ম্যাক এক্সপ্লোরার পুরো এক্সপোর্ট জিপ আনজিপ করলেও তার ভেতরে থাকা অপর জিপ ফাইলটি অটো-এক্সট্র্যাক্ট করেনি।
3. **সমাধান:** এবার সরাসরি রুটে **`Smart_Business_Manager_Release.zip`** এবং **`app-debug.apk`** রাখা হয়েছে। আপনি সরাসরি ফাইল এক্সপ্লোরার বা আর্টিফ্যাক্টস থেকে এক ক্লিকে ডাউনলোড করতে পারবেন।

---

## 📥 ৪. Google AI Studio থেকে ডাউনলোড করার সঠিক উপায়

### উপায় ক: কোড এডিটরের বামপাশের ফাইল এক্সপ্লোরার (File Explorer)
১. Google AI Studio-এর স্ক্রিনের বামপাশে ফাইল তালিকা (File Explorer tree) দেখুন।  
২. একদম উপরে রুট ডিরেক্টরিতে স্ক্রোল করুন।  
৩. সেখানে আপনি সরাসরি দেখতে পাবেন:
   - **`Smart_Business_Manager_Release.zip`** (29 MB)
   - **`app-debug.apk`** (33 MB)  
৪. ফাইলের নামের পাশে থাকা **৩টি ডট (...)** অথবা ফাইলের উপর **Right-Click** করে **"Download"** অপশনে ক্লিক করুন।

### উপায় খ: ডানপাশের Artifacts প্যানেল
১. চ্যাটের ডানপাশে বা এই মেসেজের নিচে সংযুক্ত Artifact কার্ডে যান।  
২. সেখানে **Download** আইকনে ক্লিক করে সরাসরি ফাইলটি নামিয়ে নিন।
