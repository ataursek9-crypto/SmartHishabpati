/**
 * Smart Hishabpati — Enterprise Zero-Trust Firebase Cloud Functions
 * Provides Server-Side Phone + PIN Authentication, Salted & Peppered PBKDF2 Migration,
 * Atomic Attempt Reservation Rate Limiting, Zero-Leakage Multi-Shop Verification,
 * and Transaction-Guarded Shop Registration.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");

// Initialize Firebase Admin SDK
admin.initializeApp();
const db = admin.firestore();

// Server-held secret pepper: Protects 4-digit PINs against offline cracking even if Firestore DB is leaked
const SERVER_PIN_PEPPER =
  process.env.PIN_SECRET_PEPPER ||
  "SmartHishabpati_EnterpriseZeroTrustPepper_98a72bdf41c0e39";

/**
 * Normalizes Bengali digits (০-৯) to English digits (0-9)
 */
function normalizeDigits(input) {
  if (!input) return "";
  const bengaliDigits = ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"];
  let output = String(input).trim();
  for (let i = 0; i < 10; i++) {
    output = output.split(bengaliDigits[i]).join(String(i));
  }
  return output;
}

/**
 * Normalizes Bangladeshi phone number into standard 11-digit format (e.g., 01719140500)
 */
function normalizeBangladeshiPhone(phone) {
  if (!phone) return "";
  let p = normalizeDigits(phone).replace(/[^\d+]/g, "");

  if (p.startsWith("+880")) {
    p = p.substring(3);
  } else if (p.startsWith("880")) {
    p = p.substring(2);
  } else if (p.startsWith("00880")) {
    p = p.substring(4);
  }

  if (!p.startsWith("0") && p.length === 10) {
    p = "0" + p;
  }

  return p;
}

/**
 * Validates standard 11-digit Bangladeshi mobile number (013-019)
 */
function isValidBdMobile(phone) {
  return /^01[3-9]\d{8}$/.test(phone);
}

/**
 * Timing-safe string comparison to protect against side-channel timing attacks
 */
function timingSafeEqual(strA, strB) {
  if (typeof strA !== "string" || typeof strB !== "string") return false;
  const bufA = Buffer.from(strA, "utf8");
  const bufB = Buffer.from(strB, "utf8");
  if (bufA.length !== bufB.length) {
    const dummy = Buffer.alloc(bufA.length);
    crypto.timingSafeEqual(bufA, dummy);
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Applies server-side HMAC-SHA256 pepper
 */
function applyServerPepper(pinHashInput) {
  return crypto
    .createHmac("sha256", SERVER_PIN_PEPPER)
    .update(pinHashInput)
    .digest("hex");
}

/**
 * Derives salted PBKDF2 hash using HMAC-SHA256 (100,000 iterations)
 */
function derivePbkdf2Hash(pepperedInput, saltHex, iterations = 100000) {
  const salt = Buffer.from(saltHex, "hex");
  return crypto
    .pbkdf2Sync(pepperedInput, salt, iterations, 32, "sha256")
    .toString("hex");
}

/**
 * Verifies PIN and returns upgraded hash if migration is required.
 * Supported formats:
 * 1. pbkdf2_peppered:100000:<salt>:<hash> (Latest enterprise standard)
 * 2. pbkdf2:100000:<salt>:<hash> (Salted only -> upgrades to peppered)
 * 3. 64-char hex string (Legacy plain SHA-256 -> upgrades to peppered + salted PBKDF2)
 */
function verifyPinAndCheckMigration(inputPinHash, storedPinValue) {
  if (!storedPinValue || typeof storedPinValue !== "string") {
    return { isValid: false, upgradedHash: null };
  }

  // A. Format 1: Latest pbkdf2_peppered
  if (storedPinValue.startsWith("pbkdf2_peppered:")) {
    const parts = storedPinValue.split(":");
    if (parts.length === 4) {
      const iterations = parseInt(parts[1], 10) || 100000;
      const saltHex = parts[2];
      const expectedDerived = parts[3];
      const peppered = applyServerPepper(inputPinHash);
      const derived = derivePbkdf2Hash(peppered, saltHex, iterations);
      const isValid = timingSafeEqual(derived, expectedDerived);
      return { isValid, upgradedHash: null };
    }
  }

  // B. Format 2: Legacy pbkdf2 without pepper -> verify and upgrade
  if (storedPinValue.startsWith("pbkdf2:")) {
    const parts = storedPinValue.split(":");
    if (parts.length === 4) {
      const iterations = parseInt(parts[1], 10) || 100000;
      const saltHex = parts[2];
      const expectedDerived = parts[3];
      const derived = derivePbkdf2Hash(inputPinHash, saltHex, iterations);
      const isValid = timingSafeEqual(derived, expectedDerived);
      if (isValid) {
        // Upgrade to peppered
        const newSaltHex = crypto.randomBytes(16).toString("hex");
        const newPeppered = applyServerPepper(inputPinHash);
        const newDerivedHex = derivePbkdf2Hash(newPeppered, newSaltHex, 100000);
        return {
          isValid: true,
          upgradedHash: `pbkdf2_peppered:100000:${newSaltHex}:${newDerivedHex}`,
        };
      }
      return { isValid: false, upgradedHash: null };
    }
  }

  // C. Format 3: Legacy plain SHA-256
  const isLegacyMatch = timingSafeEqual(storedPinValue, inputPinHash);
  if (isLegacyMatch) {
    const newSaltHex = crypto.randomBytes(16).toString("hex");
    const newPeppered = applyServerPepper(inputPinHash);
    const newDerivedHex = derivePbkdf2Hash(newPeppered, newSaltHex, 100000);
    return {
      isValid: true,
      upgradedHash: `pbkdf2_peppered:100000:${newSaltHex}:${newDerivedHex}`,
    };
  }

  return { isValid: false, upgradedHash: null };
}

/**
 * 1. verifyPhonePinAndCreateAuthToken
 * Secure HTTPS Callable Function
 */
exports.verifyPhonePinAndCreateAuthToken = functions.https.onCall(
  async (data, context) => {
    // App Check validation (logs warning if missing during transitional rollout)
    if (context.app === undefined) {
      console.warn("App Check token missing or unverified for caller IP:", context.rawRequest?.ip);
    }

    const rawPhone = data.phone;
    const rawPinHash = data.pinHash;
    const targetBusinessId = data.targetBusinessId ? String(data.targetBusinessId).trim() : null;

    // A. Strict Input Validation
    if (!rawPhone || typeof rawPhone !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "মোবাইল নম্বর প্রদান করা আবশ্যক।"
      );
    }

    if (!rawPinHash || typeof rawPinHash !== "string" || !/^[a-fA-F0-9]{64}$/.test(rawPinHash)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "নিরাপদ পিন হ্যাশ (৬৪ অক্ষর হেক্স) প্রদান করা আবশ্যক।"
      );
    }

    const normalizedPhone = normalizeBangladeshiPhone(rawPhone);
    if (!isValidBdMobile(normalizedPhone)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর লিখুন (যেমন: 01719140500)।"
      );
    }

    // B. Concurrency-Safe Atomic Attempt Reservation
    // Uses a single transaction to immediately reserve an attempt and prevent concurrent parallel race bypass
    const rateLimitRef = db.collection("auth_rate_limits").doc(normalizedPhone);
    const now = Date.now();
    const MAX_FAILED_ATTEMPTS = 5;
    const LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    await db.runTransaction(async (t) => {
      const doc = await t.get(rateLimitRef);
      let failedAttempts = 0;
      let lockedUntil = 0;

      if (doc.exists) {
        const rateData = doc.data();
        lockedUntil = rateData.lockedUntil || 0;
        failedAttempts = rateData.failedAttempts || 0;

        if (now < lockedUntil) {
          const remainingMinutes = Math.ceil((lockedUntil - now) / 60000);
          throw new functions.https.HttpsError(
            "resource-exhausted",
            `অতিরিক্ত ভুল পিন দেওয়ার কারণে অ্যাকাউন্টটি সাময়িকভাবে লক রয়েছে। অনুগ্রহ করে ${remainingMinutes} মিনিট পর পুনরায় চেষ্টা করুন।`
          );
        }

        // If previous lockout expired, reset attempt counter
        if (lockedUntil > 0 && now >= lockedUntil) {
          failedAttempts = 0;
          lockedUntil = 0;
        }
      }

      // Atomically reserve this attempt
      const newAttempts = failedAttempts + 1;
      const isNowLocked = newAttempts >= MAX_FAILED_ATTEMPTS;

      t.set(
        rateLimitRef,
        {
          phone: normalizedPhone,
          failedAttempts: newAttempts,
          lockedUntil: isNowLocked ? now + LOCKOUT_DURATION_MS : 0,
          lastAttemptAt: now,
          clientIp: context.rawRequest?.ip || "",
        },
        { merge: true }
      );
    });

    // C. Lookup All Associated Shops and Staff Records via Admin SDK (Privileged Bypass)
    const candidatePhones = [
      normalizedPhone,
      "+88" + normalizedPhone,
      "88" + normalizedPhone,
      rawPhone.trim(),
    ];

    const foundBusinesses = [];

    // 1. Check registered_phones index first
    const registeredPhoneDoc = await db
      .collection("registered_phones")
      .doc(normalizedPhone)
      .get();

    if (registeredPhoneDoc.exists) {
      const regData = registeredPhoneDoc.data();
      if (regData.businessId) {
        const bDoc = await db.collection("businesses").doc(regData.businessId).get();
        if (bDoc.exists) {
          foundBusinesses.push({ id: bDoc.id, ref: bDoc.ref, ...bDoc.data() });
        }
      }
    }

    // 2. Query businesses by ownerPhone across variations
    for (const p of candidatePhones) {
      const snaps = await db.collection("businesses").where("ownerPhone", "==", p).get();
      snaps.forEach((doc) => {
        if (!foundBusinesses.some((b) => b.id === doc.id)) {
          foundBusinesses.push({ id: doc.id, ref: doc.ref, ...doc.data() });
        }
      });
      const phoneSnaps = await db.collection("businesses").where("phone", "==", p).get();
      phoneSnaps.forEach((doc) => {
        if (!foundBusinesses.some((b) => b.id === doc.id)) {
          foundBusinesses.push({ id: doc.id, ref: doc.ref, ...doc.data() });
        }
      });
    }

    // 3. Ambiguous Account Check (Multiple Owners on Same Phone -> Threat Block)
    if (foundBusinesses.length > 1) {
      const distinctOwnerUids = [
        ...new Set(foundBusinesses.map((b) => b.ownerUid).filter(Boolean)),
      ];
      if (distinctOwnerUids.length > 1) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "নিরাপত্তা সতর্কতা: এই মোবাইল নম্বরের সাথে একাধিক ভিন্ন মালিকানার দোকান যুক্ত রয়েছে। অ্যাকাউন্ট সুরক্ষার জন্য সুপার এডমিনের সাথে যোগাযোগ করুন।"
        );
      }
    }

    // 4. Staff records check if not an owner
    let matchedStaffRecords = [];
    if (foundBusinesses.length === 0) {
      const staffGroupSnap = await db
        .collectionGroup("staff_by_phone")
        .where("phone", "==", normalizedPhone)
        .get();

      for (const sDoc of staffGroupSnap.docs) {
        const pBizId = sDoc.ref.parent.parent ? sDoc.ref.parent.parent.id : null;
        if (pBizId) {
          const pDoc = await db.collection("businesses").doc(pBizId).get();
          if (pDoc.exists) {
            matchedStaffRecords.push({
              staffRef: sDoc.ref,
              staffData: sDoc.data(),
              bizData: { id: pDoc.id, ref: pDoc.ref, ...pDoc.data() },
            });
          }
        }
      }
    }

    // If completely unknown phone number:
    if (foundBusinesses.length === 0 && matchedStaffRecords.length === 0) {
      throw new functions.https.HttpsError(
        "not-found",
        "এই মোবাইল নম্বর দিয়ে কোনো দোকান বা অ্যাকাউন্ট খুঁজে পাওয়া যায়নি! অনুগ্রহ করে সঠিক নম্বর দিন অথবা নতুন দোকান নিবন্ধন করুন।"
      );
    }

    // D. CRITICAL: VERIFY PIN FIRST BEFORE REVEALING ANY SHOP INFORMATION
    let isPinCorrect = false;
    let upgradedHashToSave = null;
    let docRefToMigrate = null;
    let verifiedOwnerUid = null;
    let verifiedRole = "ADMIN";
    let verifiedDisplayName = "অ্যাডমিন";
    let isStaffLogin = false;
    let activeStaffRecord = null;

    if (foundBusinesses.length > 0) {
      // Owner login: All shops belong to the same verified owner
      const sampleShop = foundBusinesses[0];
      verifiedOwnerUid = sampleShop.ownerUid || `owner_${sampleShop.id}`;
      verifiedDisplayName = sampleShop.ownerName || "অ্যাডমিন";
      verifiedRole = "ADMIN";

      let storedPin = sampleShop.ownerPinHash || sampleShop.pinHash;
      docRefToMigrate = sampleShop.ref;

      // Check subcollection staff_by_phone for owner PIN if needed
      if (!storedPin) {
        const ownerStaffDoc = await sampleShop.ref
          .collection("staff_by_phone")
          .doc(normalizedPhone)
          .get();
        if (ownerStaffDoc.exists) {
          storedPin = ownerStaffDoc.data().pinHash;
          docRefToMigrate = ownerStaffDoc.ref;
        }
      }

      const checkResult = verifyPinAndCheckMigration(rawPinHash, storedPin);
      isPinCorrect = checkResult.isValid;
      upgradedHashToSave = checkResult.upgradedHash;
    } else if (matchedStaffRecords.length > 0) {
      // Staff login: Check PIN against staff record
      // If multiple staff records exist, check if any matches the PIN
      for (const sRec of matchedStaffRecords) {
        const checkResult = verifyPinAndCheckMigration(rawPinHash, sRec.staffData.pinHash);
        if (checkResult.isValid) {
          isPinCorrect = true;
          upgradedHashToSave = checkResult.upgradedHash;
          docRefToMigrate = sRec.staffRef;
          activeStaffRecord = sRec;
          isStaffLogin = true;
          verifiedOwnerUid = sRec.staffData.userId || `staff_${normalizedPhone}`;
          verifiedDisplayName = sRec.staffData.name || "কর্মী";
          verifiedRole = sRec.staffData.role || "SALESMAN";
          break;
        }
      }
    }

    // IF PIN IS INCORRECT -> REJECT IMMEDIATELY. NO SHOP NAMES/IDS/ADDRESSES RETURNED!
    if (!isPinCorrect) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "ভুল পিন কোড প্রদান করা হয়েছে! পুনরায় সঠিক পিন দিয়ে চেষ্টা করুন।"
      );
    }

    // E. PIN IS VERIFIED! Perform Asynchronous PIN Migration AWAIT safely
    if (upgradedHashToSave && docRefToMigrate) {
      try {
        await db.runTransaction(async (t) => {
          const doc = await t.get(docRefToMigrate);
          if (doc.exists) {
            const currentSecVer = doc.data().pinSecurityVersion || 1;
            // Prevent older concurrent migration from overwriting newer version
            if (currentSecVer <= 2) {
              const updates = {
                pinHash: upgradedHashToSave,
                pinSecurityVersion: 2,
                pinMigratedAt: now,
              };
              if (doc.data().ownerPinHash !== undefined) {
                updates.ownerPinHash = upgradedHashToSave;
              }
              t.update(docRefToMigrate, updates);
            }
          }
        });
      } catch (migErr) {
        console.warn("Awaited PIN migration transaction notice:", migErr.message);
      }
    }

    // F. Reset Rate Limit upon Successful PIN Verification (Atomic Transaction)
    await db.runTransaction(async (t) => {
      t.delete(rateLimitRef);
    });

    // G. POST-VERIFICATION MULTI-SHOP ROUTING & TENANT ISOLATION
    let selectedBusiness = null;

    if (!isStaffLogin) {
      // Owner flow
      if (foundBusinesses.length === 1) {
        selectedBusiness = foundBusinesses[0];
      } else {
        // Owner has multiple shops
        if (targetBusinessId) {
          // Verify that targetBusinessId is actually owned by this verified owner
          selectedBusiness = foundBusinesses.find((b) => b.id === targetBusinessId);
          if (!selectedBusiness) {
            throw new functions.https.HttpsError(
              "permission-denied",
              "অননুমোদিত প্রবেশ! নির্বাচিত দোকানটি আপনার মালিকানাধীন নয়।"
            );
          }
        } else {
          // PIN is already verified! Safe to return shop selection list to the verified owner
          return {
            requiresShopSelection: true,
            shops: foundBusinesses.map((b) => ({
              id: b.id,
              businessName: b.businessName || b.name || "আমার দোকান",
              address: b.address || "",
              district: b.district || "",
              status: b.status || "ACTIVE",
            })),
            message: "আপনার অ্যাকাউন্টের অধীনে একাধিক দোকান রয়েছে। অনুগ্রহ করে কোন দোকানে প্রবেশ করবেন নির্বাচন করুন।",
          };
        }
      }
    } else {
      // Staff flow
      if (matchedStaffRecords.length === 1) {
        selectedBusiness = matchedStaffRecords[0].bizData;
      } else {
        if (targetBusinessId) {
          const staffMatch = matchedStaffRecords.find((s) => s.bizData.id === targetBusinessId);
          if (!staffMatch) {
            throw new functions.https.HttpsError(
              "permission-denied",
              "অননুমোদিত প্রবেশ! নির্বাচিত দোকানে আপনার কোনো কর্মী নিয়োগ নেই।"
            );
          }
          selectedBusiness = staffMatch.bizData;
          verifiedRole = staffMatch.staffData.role || "SALESMAN";
        } else {
          return {
            requiresShopSelection: true,
            shops: matchedStaffRecords.map((s) => ({
              id: s.bizData.id,
              businessName: s.bizData.businessName || s.bizData.name || "দোকান",
              address: s.bizData.address || "",
              district: s.bizData.district || "",
              status: s.bizData.status || "ACTIVE",
            })),
            message: "আপনি একাধিক প্রতিষ্ঠানে কর্মী হিসেবে নিবন্ধিত রয়েছেন। কোন প্রতিষ্ঠানে প্রবেশ করবেন নির্বাচন করুন।",
          };
        }
      }
    }

    if (!selectedBusiness) {
      throw new functions.https.HttpsError(
        "not-found",
        "নির্বাচিত দোকানটি খুঁজে পাওয়া যায়নি।"
      );
    }

    // Check Business Status (Suspension)
    if (selectedBusiness.status === "SUSPENDED") {
      throw new functions.https.HttpsError(
        "permission-denied",
        `আপনার প্রতিষ্ঠানটি ('${selectedBusiness.businessName || selectedBusiness.name}') সাময়িকভাবে স্থগিত (Suspended) করা হয়েছে। কর্তৃপক্ষের সাথে যোগাযোগ করুন।`
      );
    }

    // H. Ensure Firebase Auth User Exists
    try {
      await admin.auth().getUser(verifiedOwnerUid);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        await admin.auth().createUser({
          uid: verifiedOwnerUid,
          displayName: verifiedDisplayName,
          phoneNumber: "+88" + normalizedPhone,
        });
      }
    }

    // I. Mint Custom Token with Mandatory Claims
    const customClaims = {
      businessId: selectedBusiness.id,
      role: verifiedRole,
      phone: normalizedPhone,
      authMethod: "PHONE_PIN",
    };

    const customToken = await admin.auth().createCustomToken(verifiedOwnerUid, customClaims);

    // J. Audit Log
    try {
      await db.collection("platform_audit_logs").add({
        action: "PHONE_PIN_LOGIN_SUCCESS",
        businessId: selectedBusiness.id,
        businessName: selectedBusiness.businessName || selectedBusiness.name,
        userId: verifiedOwnerUid,
        userName: verifiedDisplayName,
        phone: normalizedPhone,
        role: verifiedRole,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
    } catch (_logErr) {}

    // K. Return Secure Result
    return {
      success: true,
      customToken: customToken,
      businessId: selectedBusiness.id,
      businessName: selectedBusiness.businessName || selectedBusiness.name || "আমার দোকান",
      ownerUid: verifiedOwnerUid,
      targetUid: verifiedOwnerUid,
      ownerName: verifiedDisplayName,
      role: verifiedRole,
      phone: normalizedPhone,
    };
  }
);

/**
 * 2. registerPhoneShopWithCustomToken
 * Secure HTTPS Callable Function
 */
exports.registerPhoneShopWithCustomToken = functions.https.onCall(
  async (data, context) => {
    // App Check validation notice
    if (context.app === undefined) {
      console.warn("App Check unverified for shop registration caller IP:", context.rawRequest?.ip);
    }

    const rawShopName = data.shopName;
    const rawOwnerName = data.ownerName;
    const rawPhone = data.phone;
    const rawPinHash = data.pinHash;
    const rawAddress = data.address || "";
    const rawDistrict = data.district || "";
    const rawBusinessType = data.businessType || "মুদি ও ডিপার্টমেন্টাল";

    // A. Strict Input Validations
    if (!rawShopName || typeof rawShopName !== "string" || rawShopName.trim().length < 2 || rawShopName.trim().length > 100) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "দোকানের নাম ২ থেকে ১০০ অক্ষরের মধ্যে হতে হবে।"
      );
    }

    if (!rawOwnerName || typeof rawOwnerName !== "string" || rawOwnerName.trim().length < 2 || rawOwnerName.trim().length > 100) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "মালিকের নাম ২ থেকে ১০০ অক্ষরের মধ্যে হতে হবে।"
      );
    }

    if (!rawPinHash || typeof rawPinHash !== "string" || !/^[a-fA-F0-9]{64}$/.test(rawPinHash)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "নিরাপদ পিন হ্যাশ (৬৪ অক্ষর হেক্স) প্রদান করা আবশ্যক।"
      );
    }

    const normalizedPhone = normalizeBangladeshiPhone(rawPhone);
    if (!isValidBdMobile(normalizedPhone)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর লিখুন (যেমন: 01719140500)।"
      );
    }

    // B. Anti-Abuse Registration Rate Limiting (Max 3 registrations per IP/Phone per hour)
    const regRateLimitRef = db.collection("registration_rate_limits").doc(normalizedPhone);
    const now = Date.now();
    await db.runTransaction(async (t) => {
      const doc = await t.get(regRateLimitRef);
      if (doc.exists) {
        const d = doc.data();
        if (d.attempts >= 3 && now - d.lastAttemptAt < 60 * 60 * 1000) {
          throw new functions.https.HttpsError(
            "resource-exhausted",
            "নিরাপত্তা সতর্কতা: অতিরিক্ত রেজিস্ট্রেশন চেষ্টার কারণে ১ ঘণ্টা পর পুনরায় চেষ্টা করুন।"
          );
        }
      }
      t.set(
        regRateLimitRef,
        {
          attempts: admin.firestore.FieldValue.increment(1),
          lastAttemptAt: now,
          clientIp: context.rawRequest?.ip || "",
        },
        { merge: true }
      );
    });

    // C. Atomic Transaction for Duplicate Phone Check & Document Creation
    const regPhoneRef = db.collection("registered_phones").doc(normalizedPhone);
    const newBizRef = db.collection("businesses").doc();
    const newBizId = newBizRef.id;
    const newOwnerUid = "owner_" + newBizId;

    // Secure Salted & Peppered PBKDF2 hash computation
    const saltHex = crypto.randomBytes(16).toString("hex");
    const peppered = applyServerPepper(rawPinHash);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const saltedPbkdf2Hash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    // STRICT PRIVILEGE LIMITATION: Plan and role are strictly non-negotiable hardcoded server-side values
    const newBizData = {
      id: newBizId,
      name: rawShopName.trim(),
      businessName: rawShopName.trim(),
      ownerName: rawOwnerName.trim(),
      ownerPhone: normalizedPhone,
      phone: normalizedPhone,
      ownerUid: newOwnerUid,
      address: String(rawAddress).trim().substring(0, 200),
      district: String(rawDistrict).trim().substring(0, 50),
      businessType: String(rawBusinessType).trim().substring(0, 50),
      ownerPinHash: saltedPbkdf2Hash,
      pinHash: saltedPbkdf2Hash,
      pinSecurityVersion: 2,
      status: "ACTIVE",
      planId: "plan_free",
      planName: "ফ্রি ট্রায়াল",
      subscriptionStatus: "ACTIVE",
      subscriptionExpiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000,
      isAutoExpiryDisabled: false,
      maxStaffAllowed: 3,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    const staffPhoneRef = newBizRef.collection("staff_by_phone").doc(normalizedPhone);

    await db.runTransaction(async (t) => {
      const regDoc = await t.get(regPhoneRef);
      if (regDoc.exists) {
        throw new functions.https.HttpsError(
          "already-exists",
          "এই মোবাইল নম্বর দিয়ে ইতিমধ্যে একটি দোকান নিবন্ধিত রয়েছে। নতুন দোকান তৈরি না করে লগইন করুন।"
        );
      }

      t.set(newBizRef, newBizData);
      t.set(regPhoneRef, {
        phone: normalizedPhone,
        businessId: newBizId,
        businessName: rawShopName.trim(),
        ownerName: rawOwnerName.trim(),
        ownerUid: newOwnerUid,
        createdAt: Date.now(),
      });
      t.set(staffPhoneRef, {
        userId: newOwnerUid,
        name: rawOwnerName.trim(),
        phone: normalizedPhone,
        role: "ADMIN",
        pinHash: saltedPbkdf2Hash,
        pinSecurityVersion: 2,
        isActive: true,
        updatedAt: Date.now(),
      });
    });

    // D. Create Firebase Auth user
    try {
      await admin.auth().getUser(newOwnerUid);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        await admin.auth().createUser({
          uid: newOwnerUid,
          displayName: rawOwnerName.trim(),
          phoneNumber: "+88" + normalizedPhone,
        });
      }
    }

    // E. Mint Custom Token with Claims
    const customClaims = {
      businessId: newBizId,
      role: "ADMIN",
      phone: normalizedPhone,
      authMethod: "PHONE_PIN",
    };
    const customToken = await admin.auth().createCustomToken(newOwnerUid, customClaims);

    return {
      success: true,
      customToken: customToken,
      businessId: newBizId,
      businessName: rawShopName.trim(),
      ownerUid: newOwnerUid,
      targetUid: newOwnerUid,
      ownerName: rawOwnerName.trim(),
      role: "ADMIN",
      phone: normalizedPhone,
    };
  }
);
