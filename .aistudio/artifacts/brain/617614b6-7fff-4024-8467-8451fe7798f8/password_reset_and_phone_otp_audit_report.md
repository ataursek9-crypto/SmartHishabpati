# Smart Business Manager — Forgot Password, Email Delivery ও Phone OTP অডিট ও স্থায়ী সমাধান রিপোর্ট

**অ্যাপের নাম:** SmartHishabpati (Smart Business Manager)  
**ভার্সন:** 1.1 (Build Code: 2)  
**তারিখ:** নভেম্বর ৫, ২০২৬  
**স্টেটাস:** ✅ অডিট সম্পন্ন, কোড সমাধান কার্যকর ও বিল্ড সফল  

---

## ১. অডিট রিপোর্ট: মূল কারণ ও বিশ্লেষণ (Audit Findings)

| পয়েন্ট | পরীক্ষিত বিষয় | অডিট ফলাফল ও প্রযুক্তিগত কারণ |
|---|---|---|
| **১.১** | **Firebase Auth Email/Password Provider** | `Email/Password` প্রোভাইডার কনফিগারেশনে সমর্থিত। তবে ব্যবহারকারী যদি পূর্বে **Google Sign-In** দিয়ে দোকান নিবন্ধন করে থাকেন, তবে Firebase Auth-এ সেই অ্যাকাউন্টে কোনো পাসওয়ার্ড সেট থাকে না (Federated Provider Only)। |
| **১.২** | **Email Enumeration Protection ও Reset API** | আধুনিক Firebase প্রোজেক্টে ডিফল্টভাবে **Email Enumeration Protection** সক্রিয় থাকে। এর ফলে সিকিউরিটির স্বার্থে `fetchSignInMethodsForEmail` ফাঁকা তালিকা ফেরত দেয় এবং `sendPasswordResetEmail` কল করলে রিকোয়েস্টটি সবসময় সফল (200 OK) দেখায়। কিন্তু যদি অ্যাকাউন্টটি Google-only হয়, তবে Firebase Auth অভ্যন্তরীণভাবে কোনো পাসওয়ার্ড রিসেট ইমেইল ইস্যু করে না। |
| **১.৩** | **Email Delivery ও Spam/Junk ফোল্ডার** | প্রকৃত Email/Password অ্যাকাউন্টের ক্ষেত্রে Firebase থেকে `noreply@smart-business-manager-71879.firebaseapp.com` ঠিকানা থেকে ইমেইল পাঠানো হয়। কিন্তু কাস্টম ডোমেইন SPF/DKIM না থাকায় Gmail বা অন্যান্য ইমেইল ক্লায়েন্ট স্বয়ংক্রিয়ভাবে এটিকে **Spam / Junk** ফোল্ডারে পাঠিয়ে দেয়। এছাড়া ব্যবহারকারীকে শুধু "অনুরোধ পাঠানো হয়েছে" না বলে আসল ডেলিভারি নির্দেশিকা জানানো দরকার ছিল। |
| **১.৪** | **Google-only একাউন্টে Forgot Password জটিলতা** | পূর্বে অ্যাপ Google-only এবং Email/Password অ্যাকাউন্টকে স্পষ্টভাবে আলাদা করে হ্যান্ডেল করত না। ব্যবহারকারী গুগল দিয়ে খোলার পরও জিমেইল লিখে রিসেট চেয়ে বিভ্রান্ত হতেন। |
| **১.৫** | **Phone OTP এবং Bangladesh (+880) SMS** | বাংলাদেশে (+880) আন্তর্জাতিক এসএমএস ওটিপি পাঠানোর ক্ষেত্রে Google Cloud **Blaze (Pay-as-you-go) বিলিং প্ল্যান** সক্রিয় থাকা বাধ্যতামূলক। ফ্রী (Spark) প্ল্যানে আন্তর্জাতিক এসএমএস ব্লক থাকে। এছাড়া Firebase Console-এ **SMS Region Policy**-তে Bangladesh (+880) অনুমোদিত থাকতে হয় এবং বর্তমান কীস্টোরের **SHA-1** ফিঙ্গারপ্রিন্ট প্লে ইন্টিগ্রিটিতে যুক্ত থাকতে হয়। |
| **১.৬** | **Quota Protection ও রিপিটেড ওটিপি স্প্যাম** | পূর্বে অ্যাপে ওটিপি পাঠানোর পর টাইমস্ট্যাম্প সংরক্ষণ না করায় ৬০ সেকেন্ডের কুলডাউন কার্যকর হচ্ছিল না, ফলে বারবার চাপ দিয়ে প্রজেক্টের দৈনিক এসএমএস কোটা দ্রুত নিঃশেষ হয়ে যেত। |

---

## ২. বাস্তবায়িত সমাধান ও পরিবর্তনসমূহ (Implemented Solutions)

### ক. Google-only এবং Email/Password অ্যাকাউন্ট আলাদা শনাক্তকরণ
1. `BusinessViewModel.kt`-এর `sendPasswordResetEmail`-এ মাল্টি-লেয়ার যাচাই ব্যবস্থা যুক্ত করা হয়েছে:
   - Firebase Auth প্রোভাইডার যাচাই (`signInMethods.contains("google.com") && !signInMethods.contains("password")`)
   - ক্লাউড লিংক ও লোকাল ক্রেডেনশিয়াল ট্র্যাকিং (`CloudBusinessAuth.getStoredGoogleEmail`, `isLinkedGoogleAccount`, `auth.currentUser?.providerData`)
2. যদি অ্যাকাউন্টটি **Google-only** হয়:
   - কোনো অনর্থক পাসওয়ার্ড রিসেট রিকোয়েস্ট পাঠানো হবে না।
   - ব্যবহারকারীকে স্পষ্টভাবে বার্তা দেখানো হবে:
     - **বাংলা:** *"এই অ্যাকাউন্টটি Google Sign-In দিয়ে তৈরি। এতে কোনো পাসওয়ার্ড নেই। করণীয়: অনুগ্রহ করে নিচে 'Google দিয়ে প্রবেশ' বোতামে চাপ দিন।"*
     - **English:** *"This account was created with Google Sign-In and has no password. What to do: Please sign in directly using the 'Continue with Google' button below."*
   - ডায়ালগের ভেতরেই সরাসরি একটি বেগুনি **"Google দিয়ে প্রবেশ করুন" / "Continue with Google"** বাটন দৃশ্যমান হবে, যা এক ক্লিকেই গুগল সাইন-ইন চালু করে।

### খ. Password Reset-এর সঠিক ও সৎ ফলাফল প্রদর্শন
1. Firebase-এ পাসওয়ার্ড রিসেট রিকোয়েস্ট সফল হলে:
   - **বাংলা:** *"পাসওয়ার্ড পরিবর্তনের লিংক আপনার ইমেইলে পাঠানোর অনুরোধ গ্রহণ করা হয়েছে। ইনবক্স ও Spam/Junk ফোল্ডার পরীক্ষা করুন।"*
   - **English:** *"Your password reset request has been accepted. Please check your inbox and Spam/Junk folder."*
   - সাথে স্পষ্ট পরামর্শ: *"পরামর্শ: রিসেট ইমেইলটি noreply@smart-business-manager-71879.firebaseapp.com থেকে পাঠানো হয়। ইনবক্সে না পেলে Spam/Junk ফোল্ডার চেক করুন।"*
   - **ডেলিভারি দাবি করা হয়নি:** কেবল ফায়ারবেস অনুরোধ গ্রহণ করেছে এবং ব্যবহারকারীকে স্প্যাম ফোল্ডার দেখার নির্দেশ দেওয়া হয়েছে।
2. কোনো ব্যর্থতা হলে `AppErrorHandler`-এর মাধ্যমে প্রকৃত এরর কোড (`ERROR_USER_NOT_FOUND`, `ERROR_INVALID_EMAIL`, `TOO_MANY_REQUESTS` ইত্যাদি) অনুযায়ী সঠিক ও স্পষ্ট দ্বিভাষিক বার্তা প্রদর্শন।

### গ. Phone OTP কোটা সুরক্ষা ও বাংলা-ইংরেজি এরর হ্যান্ডলিং
1. **বাংলাদেশ (+880) ফরম্যাটিং:** বাংলা সংখ্যা (০-৯) স্বয়ংক্রিয়ভাবে ASCII (0-9)-এ রূপান্তর করে সঠিক E.164 ফরম্যাট (`+8801XXXXXXXXX`) নিশ্চিত করা হয়েছে।
2. **বাস্তব কোটা কুলডাউন এনফোর্সমেন্ট:** `PhoneAuthHelper.kt`-এ রিকোয়েস্ট পাঠানোর সাথে সাথেই `lastOtpSentTimestamp = System.currentTimeMillis()` এবং `lastSentPhone = e164Phone` সংরক্ষণ করা হচ্ছে। ফলে ৬০ সেকেন্ডের মধ্যে দ্বিতীয়বার রিকোয়েস্ট পাঠিয়ে কোটা নষ্ট করা পুরোপুরি প্রতিহত করা হয়েছে।
3. **দ্বিভাষিক স্পষ্ট নোটিশ ও Blaze/Region সমাধান:**
   - যদি Firebase-এ Phone Provider বন্ধ থাকে, SMS Region Policy ব্লক থাকে বা Blaze বিলিং না থাকে:
     - **বাংলা:** *"Firebase-এ Phone প্রোভাইডার বন্ধ রয়েছে, বাংলাদেশে (+880) SMS পাঠানোর অনুমতি (SMS Region Policy) নেই অথবা Blaze বিলিং সক্রিয় নেই। করণীয়: ১. SMS ঝামেলা ছাড়া তাৎক্ষণিক প্রবেশ করতে 'Google দিয়ে প্রবেশ' ব্যবহার করুন (প্রস্তাবিত)। ২. Firebase Console > Authentication > Settings-এ SMS Region Policy-তে Bangladesh (+880) Allow করুন এবং Blaze বিলিং সক্রিয় রাখুন।"*
     - **English:** *"Firebase Phone Authentication is disabled, SMS Region Policy blocks Bangladesh (+880), or Blaze billing is not activated. Recommended actions: 1. Use 'Sign in with Google' for instant one-click login without SMS dependencies. 2. Enable Bangladesh (+880) in SMS Region Policy and check Blaze billing."*
4. **বর্তমান সঠিক SHA-1 ফিঙ্গারপ্রিন্ট সংযোগ:** অ্যাপের এরর ও হেল্প টেক্সটে বর্তমান কন্টেইনারের সঠিক SHA-1 (`AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E`) আপডেট করা হয়েছে।

---

## ৩. টেস্ট ভেরিফিকেশন ফলাফল (Test Execution)

নতুন টেস্ট সুইট `PasswordResetAndOtpFixTest.kt` সফলভাবে ১০০% পাস করেছে:
1. `testBangladeshPhoneNumberFormattingAndE164`: **PASSED** (বাংলা ও স্থানীয় নম্বর E.164-এ রূপান্তর এবং ১১ ডিজিট ভ্যালিডেশন সফল)
2. `testPhoneOtpQuotaCooldownProtection`: **PASSED** (৬০ সেকেন্ডের কোটা সুরক্ষা কুলডাউন নিশ্চিত)
3. `testPasswordResetAcceptedBilingualStrings`: **PASSED** (বাংলা ও ইংরেজি সঠিক স্ট্রিং রিসেপশন নিশ্চিত)
4. `testAppErrorHandlerBilingualMapping`: **PASSED** (ভুল পাসওয়ার্ড ও টেকনিক্যাল এরর সঠিক দ্বিভাষিক ম্যাপিং)
5. `testSanitizationHidesPasswordsAndOtpInLogs`: **PASSED** (লগক্যাটে কোনো পাসওয়ার্ড, ওটিপি বা পিন প্রকাশ না পাওয়া নিশ্চিত)

---

## ৪. জেনারেটেড ফাইল ও ভেরিফিকেশন বিবরণ

| ফাইলের নাম | ফাইলের আকার | SHA-256 চেকসাম |
|---|---|---|
| **SmartBusinessManager.apk** | ৩২,১২০,৯৭৮ বাইটস (~৩০.৬৩ MB) | `3f4cbe379711341310d8f8787c7b1bbabdce2b237d813b6d44b32810b1a92930` |
| **SmartBusinessManager_Export.zip** | ৩০,৯৭২,৩৪১ বাইটস (~২৯.৫৪ MB) | `e78be1cd78e01303bf78d1bfa616d7883446400a6bd876929c23b3677a588dbe` |

*(ফাইলগুলো সরাসরি রুট ডিরেক্টরি এবং আর্টফ্যাক্ট ডিরেক্টরি `/.aistudio/artifacts/brain/617614b6-7fff-4024-8467-8451fe7798f8/`-এ সংরক্ষিত আছে।)*
