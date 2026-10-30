# Smart Business Manager - APK ডাউনলোড ও ইনস্টলেশন গাইড

## ১. সরাসরি ডাউনলোড লিংক
- **প্যাকেজ ফাইল:** `Smart_Business_Manager_APK.zip`
- **ZIP-এর ভেতরের ফাইল:** `Smart_Business_Manager.apk` (সরাসরি রুট ফোল্ডারে রাখা হয়েছে)
- **ডাউনলোড URL:**
  - [Shared URL থেকে ডাউনলোড](https://ais-pre-le6jsw3cwlc7umty6fwazz-975704801740.asia-east1.run.app/Smart_Business_Manager_APK.zip)
  - [Development URL থেকে ডাউনলোড](https://ais-dev-le6jsw3cwlc7umty6fwazz-975704801740.asia-east1.run.app/Smart_Business_Manager_APK.zip)

## ২. ইনস্টলেশন নির্দেশিকা
১. জিপ (`Smart_Business_Manager_APK.zip`) ফাইলটি ডাউনলোড করে আনজিপ করুন।
২. ভিতরে থাকা `Smart_Business_Manager.apk` ফাইলটি আপনার দুটি ফোনে ইনস্টল করুন।
৩. প্রথমবার ইনস্টল করার সময় "Install unknown apps" বা "Unknown sources" পারমিশন অন করতে হতে পারে।
৪. Google Play Protect ওয়ার্নিং আসলে "Install anyway" সিলেক্ট করুন।

---

## ৩. গুরুত্বপূর্ণ: Firebase Console-এ Security Rules হালনাগাদ (One-Time Setup)
যদি আপনার ফায়ারবেস প্রজেক্টে পূর্ববর্তী রুলস থাকে, তবে Firebase Console এ গিয়ে নিচের রুলসটি পেস্ট করে **Publish** করুন (এটি ক্রস-ডিভাইস অনুমোদন ও জমা পড়ার জন্য অপরিহার্য):

1. [Firebase Console](https://console.firebase.google.com/) এ যান এবং আপনার প্রজেক্ট ওপেন করুন।
2. বাম পাশের মেনু থেকে **Build > Firestore Database** এ ক্লিক করুন।
3. উপরের ট্যাবে **Rules** এ ক্লিক করুন।
4. নিচের সম্পূর্ণ রুলস কোডটি পেস্ট করে **Publish** বাটনে চাপ দিন:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Global Safety Net: Default-deny
    match /{document=**} {
      allow read, write: if false;
    }

    function isSignedIn() {
      return request.auth != null;
    }

    function incoming() {
      return request.resource.data;
    }

    function existing() {
      return resource.data;
    }

    function isValidId(id) {
      return id is string && id.size() > 0 && id.size() <= 128 && id.matches('^[a-zA-Z0-9_\\-]+$');
    }

    // Business ownership / staff helper
    function isBusinessOwner(businessId) {
      return isSignedIn() && (
        request.auth.uid == businessId ||
        (exists(/databases/$(database)/documents/businesses/$(businessId)) &&
         get(/databases/$(database)/documents/businesses/$(businessId)).data.ownerUid == request.auth.uid) ||
        (exists(/databases/$(database)/documents/businesses/$(businessId)/staff/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/businesses/$(businessId)/staff/$(request.auth.uid)).data.role == 'ADMIN' &&
         get(/databases/$(database)/documents/businesses/$(businessId)/staff/$(request.auth.uid)).data.isActive == true) ||
        (exists(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)).data.businessId == businessId &&
         get(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)).data.role == 'ADMIN' &&
         get(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)).data.isActive == true)
      );
    }

    function isBusinessStaff(businessId) {
      return isSignedIn() && (
        request.auth.uid == businessId ||
        (exists(/databases/$(database)/documents/businesses/$(businessId)) &&
         get(/databases/$(database)/documents/businesses/$(businessId)).data.ownerUid == request.auth.uid) ||
        (exists(/databases/$(database)/documents/businesses/$(businessId)/staff/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/businesses/$(businessId)/staff/$(request.auth.uid)).data.isActive == true) ||
        (request.auth.token.email != null &&
         exists(/databases/$(database)/documents/businesses/$(businessId)/staff_by_email/$(request.auth.token.email)) &&
         get(/databases/$(database)/documents/businesses/$(businessId)/staff_by_email/$(request.auth.token.email)).data.isActive == true) ||
        (exists(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)).data.businessId == businessId &&
         get(/databases/$(database)/documents/staff_memberships/$(request.auth.uid)).data.isActive == true)
      );
    }

    // Validation blueprints
    function isValidProduct(data) {
      return data.id is string && data.id.size() <= 128 &&
             data.name is string && data.name.size() > 0 && data.name.size() <= 200 &&
             data.category is string && data.category.size() <= 100 &&
             data.sellingPrice is number && data.sellingPrice >= 0 &&
             data.currentStock is number &&
             (!('purchasePrice' in data) || data.purchasePrice is number) &&
             (!('unit' in data) || (data.unit is string && data.unit.size() <= 50)) &&
             (!('isActive' in data) || data.isActive is bool) &&
             (!('isDeleted' in data) || data.isDeleted is bool) &&
             (!('imageUri' in data) || (data.imageUri is string && data.imageUri.size() <= 1000)) &&
             (!('imageSource' in data) || (data.imageSource is string && data.imageSource.size() <= 50)) &&
             (!('imageSourceInfo' in data) || (data.imageSourceInfo is string && data.imageSourceInfo.size() <= 200));
    }

    function isStaffStockUpdate(existingData, incomingData) {
      return incomingData.diff(existingData).affectedKeys().hasOnly(['currentStock', 'updatedAt']) &&
             incomingData.currentStock is number &&
             (!('purchasePrice' in incomingData) || incomingData.purchasePrice == existingData.purchasePrice);
    }

    function isValidCustomer(data) {
      return data.id is string && data.id.size() <= 128 &&
             data.name is string && data.name.size() > 0 && data.name.size() <= 200 &&
             data.phone is string && data.phone.size() <= 30 &&
             data.currentDue is number &&
             (!('address' in data) || (data.address is string && data.address.size() <= 300)) &&
             (!('totalBilled' in data) || data.totalBilled is number) &&
             (!('totalPaid' in data) || data.totalPaid is number) &&
             (!('isDeleted' in data) || data.isDeleted is bool);
    }

    function isValidSale(data) {
      return data.invoiceNumber is string && data.invoiceNumber.size() <= 128 &&
             data.customerId is string && data.customerId.size() <= 128 &&
             data.salesmanId is string && data.salesmanId.size() <= 128 &&
             data.totalAmount is number && data.totalAmount >= 0 &&
             (!('paidAmount' in data) || (data.paidAmount is number && data.paidAmount >= 0)) &&
             (!('dueAmount' in data) || data.dueAmount is number) &&
             (!('isDeleted' in data) || data.isDeleted is bool) &&
             (!('items' in data) || data.items is list);
    }

    function isValidReceipt(data) {
      return data.receiptNumber is string && data.receiptNumber.size() <= 128 &&
             data.customerId is string && data.customerId.size() <= 128 &&
             data.amount is number && data.amount > 0 &&
             (!('isDeleted' in data) || data.isDeleted is bool);
    }

    function isValidExpense(data) {
      return data.id is string && data.id.size() <= 128 &&
             data.amount is number && data.amount > 0 &&
             data.category is string && data.category.size() <= 50 &&
             (!('status' in data) || (data.status is string && data.status in ['PENDING', 'APPROVED', 'REJECTED'])) &&
             (!('reviewedAt' in data) || data.reviewedAt is number) &&
             (!('isDeleted' in data) || data.isDeleted is bool);
    }

    function isValidProductSubmission(data) {
      return data.id is string && data.id.size() <= 128 &&
             data.name is string && data.name.size() > 0 && data.name.size() <= 200 &&
             data.category is string && data.category.size() <= 100 &&
             data.purchasePrice is number && data.purchasePrice >= 0 &&
             data.sellingPrice is number && data.sellingPrice >= 0 &&
             data.initialStock is number && data.initialStock >= 0 &&
             (!('productCode' in data) || (data.productCode is string && data.productCode.size() <= 100)) &&
             (!('unit' in data) || (data.unit is string && data.unit.size() <= 50)) &&
             (!('salesmanId' in data) || (data.salesmanId is string && data.salesmanId.size() <= 128)) &&
             (!('status' in data) || (data.status is string && data.status in ['PENDING', 'APPROVED', 'NEEDS_CORRECTION'])) &&
             (!('adminCorrectionNote' in data) || (data.adminCorrectionNote is string && data.adminCorrectionNote.size() <= 500)) &&
             (!('imageUri' in data) || (data.imageUri is string && data.imageUri.size() <= 1000));
    }

    function isValidTombstone(data) {
      return data.id is string && data.id.size() <= 128 &&
             data.tableName is string && data.tableName.size() <= 50 &&
             data.originalId is string && data.originalId.size() <= 128 &&
             (!('isPermanent' in data) || data.isPermanent is bool);
    }

    // Business Scope Root
    match /businesses/{businessId} {
      allow get: if isSignedIn();
      allow create: if isSignedIn() && (
        request.auth.uid == businessId ||
        incoming().ownerUid == request.auth.uid ||
        isBusinessOwner(businessId)
      );
      allow update, delete: if isBusinessOwner(businessId);

      // 1. Products
      match /products/{productId} {
        allow get, list: if isBusinessStaff(businessId);
        allow create: if isBusinessOwner(businessId) && isValidProduct(incoming());
        allow update: if (isBusinessOwner(businessId) && isValidProduct(incoming())) ||
                         (isBusinessStaff(businessId) && isStaffStockUpdate(existing(), incoming()));
        allow delete: if isBusinessOwner(businessId);
      }

      // 2. Customers
      match /customers/{customerId} {
        allow get, list: if isBusinessStaff(businessId);
        allow create, update: if isBusinessStaff(businessId) && isValidCustomer(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 3. Sales (Invoices) - Staff can create & sync, only Owner can delete
      match /sales/{invoiceNumber} {
        allow get, list: if isBusinessStaff(businessId);
        allow create: if isBusinessStaff(businessId) && isValidSale(incoming());
        allow update: if (isBusinessOwner(businessId) || isBusinessStaff(businessId)) && isValidSale(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 4. Payment Receipts - Staff can collect & sync, only Owner can delete
      match /payments/{receiptNumber} {
        allow get, list: if isBusinessStaff(businessId);
        allow create: if isBusinessStaff(businessId) && isValidReceipt(incoming());
        allow update: if (isBusinessOwner(businessId) || isBusinessStaff(businessId)) && isValidReceipt(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 5. Expenses - Staff can submit & sync (pending), only Owner can approve or delete
      match /expenses/{expenseId} {
        allow get, list: if isBusinessStaff(businessId);
        allow create: if isBusinessStaff(businessId) && isValidExpense(incoming()) && (isBusinessOwner(businessId) || incoming().status == 'PENDING');
        allow update: if (isBusinessOwner(businessId) || (isBusinessStaff(businessId) && existing().status == 'PENDING' && incoming().status == 'PENDING')) && isValidExpense(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 6. Product Submissions - Staff can submit & resubmit, only Owner can approve or return for correction
      match /product_submissions/{submissionId} {
        allow get, list: if isBusinessStaff(businessId);
        allow create: if isBusinessStaff(businessId) && isValidProductSubmission(incoming()) && (isBusinessOwner(businessId) || incoming().status == 'PENDING');
        allow update: if (isBusinessOwner(businessId) || (isBusinessStaff(businessId) && (existing().status == 'NEEDS_CORRECTION' || (existing().status == 'PENDING' && incoming().status == 'PENDING')))) && isValidProductSubmission(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 7. Tombstones - Prevents cross-device resurrection of deleted records
      match /tombstones/{tombstoneId} {
        allow get, list: if isBusinessStaff(businessId);
        allow create, update: if isBusinessOwner(businessId) && isValidTombstone(incoming());
        allow delete: if isBusinessOwner(businessId);
      }

      // 8. Staff Members - Owner manages staff accounts, authenticated staff can link own profile
      match /staff/{staffUid} {
        allow get: if isSignedIn() && (request.auth.uid == businessId || request.auth.uid == staffUid || isBusinessStaff(businessId));
        allow list: if isBusinessOwner(businessId) || isBusinessStaff(businessId);
        allow create, update: if isBusinessOwner(businessId) || (
          isSignedIn() && request.auth.uid == staffUid &&
          incoming().isActive == true &&
          incoming().role == 'SALESMAN' &&
          incoming().businessId == businessId
        );
        allow delete: if isBusinessOwner(businessId);
      }

      match /staff_by_phone/{phone} {
        allow get: if isSignedIn();
        allow list, create, update, delete: if isBusinessOwner(businessId);
      }

      match /staff_by_email/{email} {
        allow get: if isSignedIn() && (
          (request.auth.token.email != null && request.auth.token.email.lower() == email.lower()) ||
          isBusinessOwner(businessId)
        );
        allow list, create, update, delete: if isBusinessOwner(businessId);
      }
    }

    // Staff memberships index to allow salesmen on separate devices to resolve their authorized businessId
    match /staff_memberships/{userId} {
      allow get: if isSignedIn() && (request.auth.uid == userId || (resource != null && 'businessId' in resource.data && isBusinessOwner(resource.data.businessId)));
      allow list: if false;
      allow create, update: if isSignedIn() && (
        (incoming().businessId is string && request.auth.uid == incoming().businessId) ||
        (request.auth.uid == userId && incoming().businessId is string &&
         incoming().role == 'SALESMAN' && incoming().isActive == true)
      );
      allow delete: if isSignedIn() && (
        request.auth.uid == userId ||
        (resource != null && 'businessId' in resource.data && request.auth.uid == resource.data.businessId)
      );
    }
  }
}
```

---

## ৪. দুই ফোনে টেস্ট করার স্পষ্ট ধাপসমূহ (Manual 2-Phone Testing Procedure)

### ধাপ ১: এডমিন ফোন (Phone 1)
1. অ্যাপ ওপেন করে এডমিন হিসেবে গুগল সাইন-ইন দিয়ে লগইন করুন (যাতে ক্লাউড অথেনটিকেশন ও বিজনেস আইডি সক্রিয় হয়)।
2. **"প্রোডাক্ট অনুমোদন ও যাচাই"** স্ক্রিনে প্রবেশ করুন।
3. ডানদিকের উপরে **সিঙ্ক বাটন** চাপুন।

### ধাপ ২: সেলসম্যান ফোন (Phone 2)
1. সেলসম্যান ফোনে গুগল অ্যাকাউন্ট দিয়ে সাইন-ইন করুন এবং **"ক্লাউড বিজনেস কোড দিয়ে যুক্ত হোন"** অপশনে গিয়ে এডমিনের Business Code ও পিন দিয়ে যুক্ত হোন।
2. **"আমার জমা দেওয়া প্রোডাক্ট"** স্ক্রিনে যান।
3. ডানদিকের "+" বাটনে ক্লিক করে একটি নতুন প্রোডাক্ট (যেমন: “থ্রি পিস”, SKU-3329) জমা দিন।
4. সফলভাবে জমা হওয়ার পর এটি সেলসম্যানের তালিকায় "অপেক্ষমাণ" দেখাবে এবং সরাসরি ক্লাউড ফায়ারবেসে জমা হবে।

### ধাপ ৩: এডমিন ফোনে অনুমোদন (Phone 1)
1. এডমিন ফোনের "প্রোডাক্ট অনুমোদন ও যাচাই" স্ক্রিনে স্বয়ংক্রিয় রিয়েল-টাইম লিসেনার বা সিঙ্ক বাটনে চাপ দেওয়ার সাথে সাথে সেলসম্যানের জমা দেওয়া প্রোডাক্টটি "অপেক্ষমাণ" তালিকায় ভেসে উঠবে।
2. প্রোডাক্টটিতে ক্লিক করে বিস্তারিত যাচাই করুন এবং **"অনুমোদন করুন"** বাটনে চাপ দিন।
3. অনুমোদন সম্পন্ন হলে আইটেমটি মূল প্রোডাক্ট তালিকায় যুক্ত হবে এবং অপেক্ষমাণ তালিকা থেকে সরে "অনুমোদিত" তালিকায় যাবে।

### ধাপ ৪: সেলসম্যান ফোনে আপডেট ও খরচ অনুমোদন যাচাই
1. সেলসম্যান ফোনে "আমার জমা দেওয়া প্রোডাক্ট" স্ক্রিনে স্ট্যাটাস পরিবর্তিত হয়ে **"অনুমোদিত"** দেখাবে।
2. খরচ অনুমোদনের ক্ষেত্রে: সেলসম্যানের খরচের আবেদন এডমিন ফোনে "খরচ ও অনুমোদন ব্যবস্থাপনা" স্ক্রিনে আসবে। এডমিন অনুমোদন দিলে তা ফায়ারবেস ক্লাউডে স্থায়ীভাবে "APPROVED" হবে।
3. এরপর সেলসম্যান ফোন সিঙ্ক হলেও বা উভয় ফোনে অ্যাপ রিস্টার্ট ও রিফ্রেশ করলেও এটি আর "Pending"-এ ফিরে যাবে না; স্থায়ীভাবে "অনুমোদিত" বহাল থাকবে।
