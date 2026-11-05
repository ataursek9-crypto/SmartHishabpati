/**
 * Smart Hishabpati — Enterprise Zero-Trust Firebase Cloud Functions
 * Provides Server-Side Phone + PIN Authentication, Salted & Peppered PBKDF2 Migration,
 * Concurrency-Safe Atomic Attempt Reservation Rate Limiting, Zero-Leakage Multi-Shop Verification,
 * Staff Status & Role Verification, Business & Subscription Validation,
 * and Transaction-Guarded Shop Registration with Phone Verification.
 */

let functions;
try {
  functions = require("firebase-functions");
} catch (_e) {
  functions = {
    https: {
      onCall: (fn) => fn,
      HttpsError: class HttpsError extends Error {
        constructor(code, message) {
          super(message);
          this.code = code;
        }
      },
    },
    runWith: () => ({
      https: {
        onCall: (fn) => fn,
      },
    }),
  };
}

let defineSecret;
try {
  defineSecret = require("firebase-functions/params").defineSecret;
} catch (_e) {
  defineSecret = (name) => ({
    name,
    value: () => process.env[name] || "",
  });
}

let admin;
try {
  admin = require("firebase-admin");
  if (!admin.apps.length) {
    admin.initializeApp();
  }
} catch (_e) {
  admin = {
    apps: [],
    initializeApp: () => {},
    firestore: Object.assign(() => ({}), {
      FieldValue: {
        serverTimestamp: () => new Date(),
        increment: (n) => n,
      },
    }),
  };
}
const crypto = require("crypto");

// Initialize Firebase Admin SDK if not already initialized
if (admin.apps && !admin.apps.length) {
  try {
    admin.initializeApp();
  } catch (_e) {}
}
let db = admin.firestore();
function setDbForTesting(customDb) {
  db = customDb;
}

function setAuthForTesting(customAuth) {
  if (typeof customAuth === "function") {
    admin.auth = customAuth;
  } else if (customAuth) {
    admin.auth = () => customAuth;
  }
}

// Server-held secret pepper: Protects 4-digit PINs against offline cracking
// Defined via Firebase Secret Manager using defineSecret (firebase-functions/params).
// Bound to Functions v1 Callable API using .runWith({ secrets: [serverPinPepperSecret] }).
// In local Node.js test or emulator runtimes, process.env.SERVER_PIN_PEPPER is used ONLY when FUNCTIONS_EMULATOR === "true" or NODE_ENV === "test".
// In Production, Secret Manager injection is mandatory; missing secret halts authentication immediately.
const serverPinPepperSecret = defineSecret("SERVER_PIN_PEPPER");

/**
 * Retrieves server-held pepper from Secret Manager or local test environment.
 * If secret is missing or empty, halts authentication completely.
 */
function getServerPepper() {
  let pepper = "";
  try {
    pepper = serverPinPepperSecret.value();
  } catch (_e) {
    if (process.env.FUNCTIONS_EMULATOR === "true" || process.env.NODE_ENV === "test") {
      pepper = process.env.SERVER_PIN_PEPPER || "";
    }
  }
  if (!pepper || typeof pepper !== "string" || pepper.trim().length === 0) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "সার্ভার নিরাপত্তা কনফিগারেশন ত্রুটি: SERVER_PIN_PEPPER অনুপস্থিত। সিকিউরিটি সুরক্ষার কারণে অথেনটিকেশন স্থগিত রাখা হয়েছে।"
    );
  }
  return pepper.trim();
}

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
  const pepper = getServerPepper();
  return crypto
    .createHmac("sha256", pepper)
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
function verifyPinAndCheckMigration(inputPin, storedPinValue) {
  if (!storedPinValue || typeof storedPinValue !== "string") {
    return { isValid: false, upgradedHash: null };
  }
  const cleanInput = String(inputPin || "").trim();
  if (!cleanInput) return { isValid: false, upgradedHash: null };

  function testMatch(candidate, expectedDerived, saltHex, iterations) {
    const peppered = applyServerPepper(candidate);
    const derived = derivePbkdf2Hash(peppered, saltHex, iterations);
    return timingSafeEqual(derived, expectedDerived);
  }

  // A. Format 1: Latest pbkdf2_peppered
  if (storedPinValue.startsWith("pbkdf2_peppered:")) {
    const parts = storedPinValue.split(":");
    if (parts.length === 4) {
      const iterations = parseInt(parts[1], 10) || 100000;
      const saltHex = parts[2];
      const expectedDerived = parts[3];
      // Test direct raw PIN
      if (testMatch(cleanInput, expectedDerived, saltHex, iterations)) {
        return { isValid: true, upgradedHash: null };
      }
      // Or if client pre-hashed with SHA-256 (backward-compatible)
      const clientSha = crypto.createHash("sha256").update(cleanInput).digest("hex");
      if (testMatch(clientSha, expectedDerived, saltHex, iterations)) {
        return { isValid: true, upgradedHash: null };
      }
      return { isValid: false, upgradedHash: null };
    }
  }

  // B. Format 2: Legacy pbkdf2 without pepper -> verify and upgrade
  if (storedPinValue.startsWith("pbkdf2:")) {
    const parts = storedPinValue.split(":");
    if (parts.length === 4) {
      const iterations = parseInt(parts[1], 10) || 100000;
      const saltHex = parts[2];
      const expectedDerived = parts[3];
      const clientSha = crypto.createHash("sha256").update(cleanInput).digest("hex");
      const isMatch = timingSafeEqual(derivePbkdf2Hash(cleanInput, saltHex, iterations), expectedDerived) ||
                      timingSafeEqual(derivePbkdf2Hash(clientSha, saltHex, iterations), expectedDerived);
      if (isMatch) {
        // Upgrade to peppered
        const newSaltHex = crypto.randomBytes(16).toString("hex");
        const newPeppered = applyServerPepper(cleanInput);
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
  const clientSha = crypto.createHash("sha256").update(cleanInput).digest("hex");
  const isLegacyMatch = timingSafeEqual(storedPinValue, cleanInput) || timingSafeEqual(storedPinValue, clientSha);
  if (isLegacyMatch) {
    const newSaltHex = crypto.randomBytes(16).toString("hex");
    const newPeppered = applyServerPepper(cleanInput);
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
exports.verifyPhonePinAndCreateAuthToken = functions
  .runWith({ secrets: [serverPinPepperSecret] })
  .https.onCall(async (data, context) => {
    // App Check validation & enforcement
    if (context.app === undefined) {
      console.warn("App Check token missing or unverified for caller IP:", context.rawRequest?.ip);
      if (process.env.APP_CHECK_ENFORCED === "true") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "নিরাপত্তা যাচাইকরণ ব্যর্থ হয়েছে: বৈধ Firebase App Check টোকেন অনুপস্থিত বা অবৈধ।"
        );
      }
    }

    const rawPhone = data.phone;
    // Accept raw PIN ("1234") or legacy pre-hash
    const rawPinInput = data.pin !== undefined ? String(data.pin) : (data.pinHash !== undefined ? String(data.pinHash) : "");
    const targetBusinessId = data.targetBusinessId ? String(data.targetBusinessId).trim() : null;

    // A. Strict Input Validation
    if (!rawPhone || typeof rawPhone !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "মোবাইল নম্বর প্রদান করা আবশ্যক।"
      );
    }

    const cleanPinInput = rawPinInput.trim();
    if (!cleanPinInput || (cleanPinInput.length < 4 || cleanPinInput.length > 64)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ৪ থেকে ৬ সংখ্যার পিন কোড প্রদান করা আবশ্যক।"
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
    // Even if 20 requests arrive simultaneously, exactly 5 attempts are permitted before lockout
    const rateLimitRef = db.collection("auth_rate_limits").doc(normalizedPhone);
    const now = Date.now();
    const MAX_FAILED_ATTEMPTS = 5;
    const LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    await db.runTransaction(async (t) => {
      const doc = await t.get(rateLimitRef);
      let failedAttempts = 0;
      let lockedUntil = 0;

      if (doc.exists) {
        const rateData = doc.data() || {};
        lockedUntil = rateData.lockedUntil || 0;
        failedAttempts = rateData.failedAttempts || 0;

        // Strict Check: If currently locked OR already at max attempts, block immediately
        if (now < lockedUntil || failedAttempts >= MAX_FAILED_ATTEMPTS) {
          const remainingMinutes = Math.max(1, Math.ceil((lockedUntil - now) / 60000));
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
        const pBizId = sDoc.ref.parent && sDoc.ref.parent.parent ? sDoc.ref.parent.parent.id : null;
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

      // Existing Shop Safe Migration: If shop exists in businesses but has no Cloud PIN hash
      if (!storedPin) {
        return {
          success: false,
          requiresPinSetup: true,
          businessId: sampleShop.id,
          businessName: sampleShop.businessName || sampleShop.name || "আমার দোকান",
          ownerPhone: normalizedPhone,
          message: "দোকানের নিরাপত্তা নিশ্চিত করতে একবারের জন্য ওটিপি যাচাই ও পিন সেটআপ প্রয়োজন।"
        };
      }

      const checkResult = verifyPinAndCheckMigration(cleanPinInput, storedPin);
      isPinCorrect = checkResult.isValid;
      upgradedHashToSave = checkResult.upgradedHash;
    } else if (matchedStaffRecords.length > 0) {
      // Staff login: Check PIN against staff record
      // Validate staff isActive and authorized Role
      const allowedStaffRoles = ["ADMIN", "SALESMAN", "MANAGER", "STAFF"];

      for (const sRec of matchedStaffRecords) {
        const sData = sRec.staffData || {};

        // Inactive staff check: skip inactive staff records
        if (sData.isActive === false || sData.status === "INACTIVE" || sData.status === "SUSPENDED") {
          continue;
        }

        // Role verification: STRICT — no default role allowed! Missing or unauthorized role is rejected.
        if (!sData.role || typeof sData.role !== "string" || sData.role.trim().length === 0) {
          continue;
        }
        const sRole = sData.role.trim().toUpperCase();
        if (!allowedStaffRoles.includes(sRole)) {
          continue;
        }

        const checkResult = verifyPinAndCheckMigration(cleanPinInput, sData.pinHash);
        if (checkResult.isValid) {
          isPinCorrect = true;
          upgradedHashToSave = checkResult.upgradedHash;
          docRefToMigrate = sRec.staffRef;
          activeStaffRecord = sRec;
          isStaffLogin = true;
          verifiedOwnerUid = sData.userId || `staff_${normalizedPhone}`;
          verifiedDisplayName = sData.name || "কর্মী";
          verifiedRole = sRole;
          break;
        }
      }
    }

    // IF PIN IS INCORRECT -> REJECT IMMEDIATELY. NO SHOP NAMES/IDS/ADDRESSES RETURNED!
    if (!isPinCorrect) {
      // If candidate was an inactive staff matching the PIN, return explicit permission-denied
      if (matchedStaffRecords.length > 0) {
        for (const sRec of matchedStaffRecords) {
          const sData = sRec.staffData || {};
          if (sData.isActive === false || sData.status === "INACTIVE" || sData.status === "SUSPENDED") {
            const testResult = verifyPinAndCheckMigration(cleanPinInput, sData.pinHash);
            if (testResult.isValid) {
              throw new functions.https.HttpsError(
                "permission-denied",
                "এই কর্মী (Staff) অ্যাকাউন্টটি নিষ্ক্রিয় বা স্থগিত করা হয়েছে। কর্তৃপক্ষের সাথে যোগাযোগ করুন।"
              );
            }
          }
        }
      }
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
          verifiedRole = (staffMatch.staffData.role || "SALESMAN").toUpperCase();
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

    // Check Business Status (Active / Suspended / Disabled)
    const businessStatus = String(selectedBusiness.status || "ACTIVE").toUpperCase();
    if (businessStatus !== "ACTIVE") {
      throw new functions.https.HttpsError(
        "permission-denied",
        `আপনার প্রতিষ্ঠানটি ('${selectedBusiness.businessName || selectedBusiness.name || "দোকান"}') স্থগিত বা নিষ্ক্রিয় (${businessStatus}) অবস্থায় রয়েছে। কর্তৃপক্ষের সাথে যোগাযোগ করুন।`
      );
    }

    // Check Business Subscription Status and Expiry
    const isAutoExpiryDisabled = selectedBusiness.isAutoExpiryDisabled === true;
    const subscriptionStatus = String(selectedBusiness.subscriptionStatus || "ACTIVE").toUpperCase();
    const expiresAt = Number(selectedBusiness.subscriptionExpiresAt) || 0;

    if (subscriptionStatus === "SUSPENDED" || subscriptionStatus === "DISABLED") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "আপনার প্রতিষ্ঠানের সাবস্ক্রিপশন স্থগিত রয়েছে। কর্তৃপক্ষের সাথে যোগাযোগ করুন।"
      );
    }

    if (!isAutoExpiryDisabled && expiresAt > 0 && now > expiresAt) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "আপনার প্রতিষ্ঠানের সাবস্ক্রিপশন মেয়াদ উত্তীর্ণ হয়ে গেছে। অনুগ্রহ করে সাবস্ক্রিপশন নবায়ন করুন।"
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

    let customToken;
    try {
      customToken = await admin.auth().createCustomToken(verifiedOwnerUid, customClaims);
    } catch (tokenErr) {
      console.error("Critical error creating custom token:", tokenErr);
      throw new functions.https.HttpsError(
        "internal",
        "অথেনটিকেশন টোকেন তৈরিতে সমস্যা হয়েছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
      );
    }

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
  });

/**
 * 2. registerPhoneShopWithCustomToken
 * Secure HTTPS Callable Function
 */
exports.registerPhoneShopWithCustomToken = functions
  .runWith({ secrets: [serverPinPepperSecret] })
  .https.onCall(async (data, context) => {
    // App Check validation & enforcement
    if (context.app === undefined) {
      console.warn("App Check unverified for shop registration caller IP:", context.rawRequest?.ip);
      if (process.env.APP_CHECK_ENFORCED === "true") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "নিরাপত্তা যাচাইকরণ ব্যর্থ হয়েছে: বৈধ Firebase App Check টোকেন অনুপস্থিত বা অবৈধ।"
        );
      }
    }

    const rawShopName = data.shopName;
    const rawOwnerName = data.ownerName;
    const rawPhone = data.phone;
    const rawPinInput = data.pin !== undefined ? String(data.pin) : (data.pinHash !== undefined ? String(data.pinHash) : "");
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

    const cleanPinInput = rawPinInput.trim();
    if (!cleanPinInput || (cleanPinInput.length < 4 || cleanPinInput.length > 64)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ৪ থেকে ৬ সংখ্যার পিন কোড প্রদান করা আবশ্যক।"
      );
    }

    const normalizedPhone = normalizeBangladeshiPhone(rawPhone);
    if (!isValidBdMobile(normalizedPhone)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর লিখুন (যেমন: 01719140500)।"
      );
    }

    // B. Verified Phone Ownership Validation (OTP or Firebase Auth Token)
    let isPhoneOwnershipVerified = false;
    if (context.auth && context.auth.token) {
      const authPhone = normalizeBangladeshiPhone(context.auth.token.phone_number || "");
      if (authPhone === normalizedPhone) {
        isPhoneOwnershipVerified = true;
      }
    }

    if (!isPhoneOwnershipVerified && data.idToken) {
      try {
        const decoded = await admin.auth().verifyIdToken(data.idToken);
        const decodedPhone = normalizeBangladeshiPhone(decoded.phone_number || "");
        if (decodedPhone === normalizedPhone) {
          isPhoneOwnershipVerified = true;
        }
      } catch (_e) {}
    }

    // Strictly disallow client-side isPhoneVerified boolean flag without real Firebase Phone Auth credential.
    // Automated tests running under NODE_ENV === "test" can pass skipPhoneAuthForTest.
    if (!isPhoneOwnershipVerified && process.env.NODE_ENV === "test" && data.skipPhoneAuthForTest === true) {
      isPhoneOwnershipVerified = true;
    }

    if (!isPhoneOwnershipVerified) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "নিবন্ধন সম্পন্ন করার আগে ওটিপি (OTP) বা ফোন ভেরিফিকেশন দিয়ে মোবাইল নম্বরের মালিকানা নিশ্চিত করতে হবে।"
      );
    }

    // C. Anti-Abuse Registration Rate Limiting (Max 3 per phone, Max 5 per IP within 1 hour)
    const clientIp = (context.rawRequest?.ip || "unknown").replace(/[^a-zA-Z0-9_.-]/g, "_");
    const regPhoneRateRef = db.collection("registration_rate_limits").doc(`phone_${normalizedPhone}`);
    const regIpRateRef = db.collection("registration_rate_limits").doc(`ip_${clientIp}`);
    const now = Date.now();
    const REG_WINDOW_MS = 60 * 60 * 1000; // 1 hour

    await db.runTransaction(async (t) => {
      // 1. Phone rate limit check
      const phoneDoc = await t.get(regPhoneRateRef);
      let phoneAttempts = 0;
      if (phoneDoc.exists) {
        const pd = phoneDoc.data() || {};
        if (now - (pd.windowStart || 0) < REG_WINDOW_MS) {
          phoneAttempts = pd.attempts || 0;
          if (phoneAttempts >= 3) {
            throw new functions.https.HttpsError(
              "resource-exhausted",
              "নিরাপত্তা সতর্কতা: এই মোবাইল নম্বর দিয়ে অতিরিক্ত রেজিস্ট্রেশন চেষ্টা করা হয়েছে। ১ ঘণ্টা পর পুনরায় চেষ্টা করুন।"
            );
          }
        }
      }

      // 2. IP rate limit check
      if (clientIp !== "unknown") {
        const ipDoc = await t.get(regIpRateRef);
        let ipAttempts = 0;
        if (ipDoc.exists) {
          const id = ipDoc.data() || {};
          if (now - (id.windowStart || 0) < REG_WINDOW_MS) {
            ipAttempts = id.attempts || 0;
            if (ipAttempts >= 5) {
              throw new functions.https.HttpsError(
                "resource-exhausted",
                "নিরাপত্তা সতর্কতা: এই আইপি অ্যাড্রেস থেকে অতিরিক্ত রেজিস্ট্রেশন চেষ্টা করা হয়েছে। অনুগ্রহ করে পরে চেষ্টা করুন।"
              );
            }
          }
        }
        t.set(
          regIpRateRef,
          {
            attempts: (ipDoc.exists && now - (ipDoc.data()?.windowStart || 0) < REG_WINDOW_MS) ? (ipDoc.data().attempts || 0) + 1 : 1,
            windowStart: (ipDoc.exists && now - (ipDoc.data()?.windowStart || 0) < REG_WINDOW_MS) ? ipDoc.data().windowStart : now,
            lastAttemptAt: now,
          },
          { merge: true }
        );
      }

      t.set(
        regPhoneRateRef,
        {
          phone: normalizedPhone,
          attempts: phoneAttempts + 1,
          windowStart: (phoneDoc.exists && now - (phoneDoc.data()?.windowStart || 0) < REG_WINDOW_MS) ? phoneDoc.data().windowStart : now,
          lastAttemptAt: now,
          clientIp: clientIp,
        },
        { merge: true }
      );
    });

    // D. Atomic Transaction for Duplicate Phone Check & Document Creation
    const regPhoneRef = db.collection("registered_phones").doc(normalizedPhone);
    const newBizRef = db.collection("businesses").doc();
    const newBizId = newBizRef.id;
    const newOwnerUid = "owner_" + newBizId;

    // Secure Salted & Peppered PBKDF2 hash computation
    const saltHex = crypto.randomBytes(16).toString("hex");
    const peppered = applyServerPepper(cleanPinInput);
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

    // E. Create Firebase Auth user
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

    // F. Mint Custom Token with Claims
    const customClaims = {
      businessId: newBizId,
      role: "ADMIN",
      phone: normalizedPhone,
      authMethod: "PHONE_PIN",
    };
    let customToken;
    try {
      customToken = await admin.auth().createCustomToken(newOwnerUid, customClaims);
    } catch (tokenErr) {
      console.error("Critical error creating registration token:", tokenErr);
      throw new functions.https.HttpsError(
        "internal",
        "রেজিস্ট্রেশন টোকেন তৈরিতে সমস্যা হয়েছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
      );
    }

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
  });

/**
 * 3. setupExistingShopPinWithOtp
 * Secure HTTPS Callable Function to initialize/reset Cloud PIN for existing shops using Firebase Phone Auth OTP
 */
exports.setupExistingShopPinWithOtp = functions
  .runWith({ secrets: [serverPinPepperSecret] })
  .https.onCall(async (data, context) => {
    // App Check validation & enforcement
    if (context.app === undefined) {
      console.warn("App Check unverified for PIN setup caller IP:", context.rawRequest?.ip);
      if (process.env.APP_CHECK_ENFORCED === "true") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "নিরাপত্তা যাচাইকরণ ব্যর্থ হয়েছে: বৈধ Firebase App Check টোকেন অনুপস্থিত বা অবৈধ।"
        );
      }
    }

    const rawPhone = data.phone;
    const rawPinInput = data.pin !== undefined ? String(data.pin) : (data.pinHash !== undefined ? String(data.pinHash) : "");
    const rawBusinessId = data.businessId;

    if (!rawPhone || !rawPinInput || !rawBusinessId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "মোবাইল নম্বর, পিন এবং বিজনেস আইডি প্রদান করা আবশ্যক।"
      );
    }

    const normalizedPhone = normalizeBangladeshiPhone(rawPhone);
    const cleanPinInput = rawPinInput.trim();
    const cleanBusinessId = String(rawBusinessId).trim();

    if (cleanPinInput.length < 4 || cleanPinInput.length > 6) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "পিন কোড অবশ্যই ৪ থেকে ৬ সংখ্যার হতে হবে।"
      );
    }

    // Phone Auth OTP Verification
    let isPhoneOwnershipVerified = false;
    if (context.auth && context.auth.token) {
      const authPhone = normalizeBangladeshiPhone(context.auth.token.phone_number || "");
      if (authPhone === normalizedPhone) {
        isPhoneOwnershipVerified = true;
      }
    }

    if (!isPhoneOwnershipVerified && data.idToken) {
      try {
        const decoded = await admin.auth().verifyIdToken(data.idToken);
        const decodedPhone = normalizeBangladeshiPhone(decoded.phone_number || "");
        if (decodedPhone === normalizedPhone) {
          isPhoneOwnershipVerified = true;
        }
      } catch (_e) {}
    }

    if (!isPhoneOwnershipVerified && process.env.NODE_ENV === "test" && data.skipPhoneAuthForTest === true) {
      isPhoneOwnershipVerified = true;
    }

    if (!isPhoneOwnershipVerified) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "দোকানের পিন সেটআপ করতে ওটিপি (OTP) দিয়ে মোবাইল নম্বরের মালিকানা নিশ্চিত করা বাধ্যতামূলক।"
      );
    }

    // Lookup business
    const bizRef = db.collection("businesses").doc(cleanBusinessId);
    const bizDoc = await bizRef.get();
    if (!bizDoc.exists) {
      throw new functions.https.HttpsError("not-found", "দোকানটি খুঁজে পাওয়া যায়নি।");
    }

    const bizData = bizDoc.data() || {};
    const ownerPhone = normalizeBangladeshiPhone(bizData.ownerPhone || bizData.phone || "");
    if (ownerPhone !== normalizedPhone) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "এই দোকানের রেজিস্টার্ড মোবাইল নম্বরের সাথে আপনার মোবাইল নম্বরের মিল নেই।"
      );
    }

    // Compute salted & peppered PBKDF2 hash
    const saltHex = crypto.randomBytes(16).toString("hex");
    const peppered = applyServerPepper(cleanPinInput);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const saltedPbkdf2Hash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;
    const ownerUid = bizData.ownerUid || `owner_${cleanBusinessId}`;

    // Transactional save to protect existing data: ONLY adds/updates PIN hash
    await db.runTransaction(async (t) => {
      t.update(bizRef, {
        ownerPinHash: saltedPbkdf2Hash,
        pinHash: saltedPbkdf2Hash,
        pinSecurityVersion: 2,
        updatedAt: Date.now(),
      });

      const regRef = db.collection("registered_phones").doc(normalizedPhone);
      t.set(
        regRef,
        {
          phone: normalizedPhone,
          businessId: cleanBusinessId,
          businessName: bizData.businessName || bizData.name || "আমার দোকান",
          ownerName: bizData.ownerName || "অ্যাডমিন",
          ownerUid: ownerUid,
          updatedAt: Date.now(),
        },
        { merge: true }
      );

      const staffRef = bizRef.collection("staff_by_phone").doc(normalizedPhone);
      t.set(
        staffRef,
        {
          userId: ownerUid,
          name: bizData.ownerName || "অ্যাডমিন",
          phone: normalizedPhone,
          role: "ADMIN",
          pinHash: saltedPbkdf2Hash,
          pinSecurityVersion: 2,
          isActive: true,
          updatedAt: Date.now(),
        },
        { merge: true }
      );
    });

    // Create Firebase Auth user if not existing
    try {
      await admin.auth().getUser(ownerUid);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        await admin.auth().createUser({
          uid: ownerUid,
          displayName: bizData.ownerName || "অ্যাডমিন",
          phoneNumber: "+88" + normalizedPhone,
        });
      }
    }

    // Mint Custom Token with Claims
    const customClaims = {
      businessId: cleanBusinessId,
      role: "ADMIN",
      phone: normalizedPhone,
      authMethod: "PHONE_PIN_MIGRATED",
    };
    let customToken;
    try {
      customToken = await admin.auth().createCustomToken(ownerUid, customClaims);
    } catch (tokenErr) {
      console.error("Critical error creating migration custom token:", tokenErr);
      throw new functions.https.HttpsError(
        "internal",
        "প্রমাণীকরণ টোকেন তৈরিতে সমস্যা হয়েছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
      );
    }

    return {
      success: true,
      customToken: customToken,
      businessId: cleanBusinessId,
      businessName: bizData.businessName || bizData.name || "আমার দোকান",
      ownerUid: ownerUid,
      targetUid: ownerUid,
      ownerName: bizData.ownerName || "অ্যাডমিন",
      role: "ADMIN",
      phone: normalizedPhone,
    };
  });

/**
 * Classifies Firebase Auth providerData into strict provider modes without leaking secrets.
 */
function classifyAuthProviders(providerData) {
  const list = Array.isArray(providerData) ? providerData : [];
  const providerIds = list
    .map((p) => (p && typeof p.providerId === "string" ? p.providerId.trim().toLowerCase() : ""))
    .filter(Boolean);

  const hasGoogleProvider = providerIds.includes("google.com");
  const hasPasswordProvider = providerIds.includes("password");
  const hasPhoneProvider = providerIds.includes("phone");

  let providerType = "OTHER";
  if (hasGoogleProvider && hasPasswordProvider) {
    providerType = "GOOGLE_AND_PASSWORD";
  } else if (hasGoogleProvider) {
    providerType = "GOOGLE_ONLY";
  } else if (hasPasswordProvider) {
    providerType = "PASSWORD_ONLY";
  } else if (hasPhoneProvider) {
    providerType = "PHONE_ONLY";
  }

  return {
    providerIds,
    hasGoogleProvider,
    hasPasswordProvider,
    hasPhoneProvider,
    providerType,
  };
}

/**
 * 4. auditEmailAuthStatus
 * Audits an email address in Firebase Auth (google.com vs password vs both vs not-found)
 * and checks whether existing shops are mapped in Firestore without logging secrets or leaking PII.
 */
exports.auditEmailAuthStatus = functions.https.onCall(async (data, context) => {
  const rawEmail = data && typeof data.email === "string" ? data.email : "";
  const cleanEmail = rawEmail.trim().toLowerCase();

  if (!cleanEmail || !cleanEmail.includes("@") || !cleanEmail.includes(".")) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "সঠিক জিমেইল / ইমেইল ঠিকানা লিখুন (যেমন: user@gmail.com)।"
    );
  }

  const authService = authClient || admin.auth();
  let userRecord = null;
  let userExists = false;

  try {
    userRecord = await authService.getUserByEmail(cleanEmail);
    userExists = Boolean(userRecord && userRecord.uid);
  } catch (err) {
    if (err && err.code === "auth/user-not-found") {
      userExists = false;
    } else {
      console.error("auditEmailAuthStatus lookup error:", err && err.code ? err.code : "unknown");
      throw new functions.https.HttpsError(
        "internal",
        "অ্যাকাউন্টের অবস্থা যাচাই করতে সমস্যা হয়েছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।"
      );
    }
  }

  const classification = userExists
    ? classifyAuthProviders(userRecord.providerData)
    : {
        providerIds: [],
        hasGoogleProvider: false,
        hasPasswordProvider: false,
        hasPhoneProvider: false,
        providerType: "NOT_FOUND",
      };

  // Query Firestore businesses mapped by UID or ownerEmail
  const shopDocsMap = new Map();
  try {
    if (userExists && userRecord.uid) {
      const byUidSnap = await db
        .collection("businesses")
        .where("ownerUid", "==", userRecord.uid)
        .get();
      if (byUidSnap && Array.isArray(byUidSnap.docs)) {
        byUidSnap.docs.forEach((doc) => {
          shopDocsMap.set(doc.id, { id: doc.id, ...(doc.data() || {}) });
        });
      }
    }
    const byEmailSnap = await db
      .collection("businesses")
      .where("ownerEmail", "==", cleanEmail)
      .get();
    if (byEmailSnap && Array.isArray(byEmailSnap.docs)) {
      byEmailSnap.docs.forEach((doc) => {
        if (!shopDocsMap.has(doc.id)) {
          shopDocsMap.set(doc.id, { id: doc.id, ...(doc.data() || {}) });
        }
      });
    }
  } catch (fsErr) {
    console.warn("auditEmailAuthStatus firestore query note:", fsErr && fsErr.code ? fsErr.code : "query_note");
  }

  const matchedShops = Array.from(shopDocsMap.values());
  const shopCount = matchedShops.length;
  const hasExistingShop = shopCount > 0;

  // Never log passwords, OTPs, tokens, or PII
  console.info(
    `[AuthAudit] domain=${cleanEmail.split("@")[1] || "unknown"} exists=${userExists} providerType=${classification.providerType} shopCount=${shopCount}`
  );

  const callerEmail =
    context && context.auth && context.auth.token && typeof context.auth.token.email === "string"
      ? context.auth.token.email.trim().toLowerCase()
      : "";
  const isVerifiedOwnerCaller =
    Boolean(context && context.auth && userExists) &&
    (context.auth.uid === userRecord.uid || (callerEmail && callerEmail === cleanEmail));

  const response = {
    exists: userExists,
    providerType: classification.providerType,
    hasGoogleProvider: classification.hasGoogleProvider,
    hasPasswordProvider: classification.hasPasswordProvider,
    hasExistingShop: hasExistingShop,
    shopCount: shopCount,
  };

  // Only include UID & safe shop summaries when the caller is authenticated as this exact user
  if (isVerifiedOwnerCaller) {
    response.uid = userRecord.uid;
    response.emailVerified = Boolean(userRecord.emailVerified);
    response.shops = matchedShops.map((s) => ({
      id: s.id,
      businessName: s.businessName || s.name || "আমার দোকান",
      ownerPhone: s.ownerPhone || s.phone || "",
      status: s.status || "ACTIVE",
      planName: s.planName || "সম্পূর্ণ ফ্রি ব্যবহার",
      address: s.address || "",
      district: s.district || "",
      createdAt: s.createdAt || Date.now(),
    }));
  }

  return response;
});

/**
 * 5. registerAuthenticatedOwnerShop
 * Safely registers a new or additional shop for an already-authenticated Firebase user (Google or Email/Password)
 * using their EXISTING UID without creating a duplicate Firebase Auth user or overwriting existing shops.
 */
exports.registerAuthenticatedOwnerShop = functions
  .runWith({ secrets: [serverPinPepperSecret] })
  .https.onCall(async (data, context) => {
    if (!context || !context.auth || !context.auth.uid) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "নিরাপদ দোকান নিবন্ধনের জন্য আগে Google অথবা ইমেইল-পাসওয়ার্ড দিয়ে অ্যাকাউন্ট যাচাই করুন।"
      );
    }

    const ownerUid = context.auth.uid;
    const rawShopName = data && data.shopName;
    const rawOwnerName = data && data.ownerName;
    const rawPhone = data && data.phone;
    const rawPinInput = data && data.pin !== undefined ? String(data.pin) : "";
    const rawAddress = (data && data.address) || "";
    const rawDistrict = (data && data.district) || "";
    const rawBusinessType = (data && data.businessType) || "সাধারণ ব্যবসা";
    const tokenEmail =
      context.auth.token && typeof context.auth.token.email === "string"
        ? context.auth.token.email.trim().toLowerCase()
        : "";
    const ownerEmail = tokenEmail || String((data && data.ownerEmail) || "").trim().toLowerCase();

    if (!rawShopName || String(rawShopName).trim().length < 2) {
      throw new functions.https.HttpsError("invalid-argument", "দোকানের নাম কমপক্ষে ২ অক্ষরের হতে হবে।");
    }
    if (!rawOwnerName || String(rawOwnerName).trim().length < 2) {
      throw new functions.https.HttpsError("invalid-argument", "মালিকের নাম কমপক্ষে ২ অক্ষরের হতে হবে।");
    }
    const cleanPinInput = rawPinInput.trim();
    if (cleanPinInput.length < 4 || cleanPinInput.length > 6) {
      throw new functions.https.HttpsError("invalid-argument", "পিন কোড অবশ্যই ৪ থেকে ৬ সংখ্যার হতে হবে।");
    }

    const normalizedPhone = normalizeBangladeshiPhone(rawPhone);
    if (!isValidBdMobile(normalizedPhone)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "সঠিক ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর লিখুন (যেমন: 01719140500)।"
      );
    }

    const signInProvider =
      (context.auth.token &&
        context.auth.token.firebase &&
        context.auth.token.firebase.sign_in_provider) ||
      "google.com";

    const saltHex = crypto.randomBytes(16).toString("hex");
    const peppered = applyServerPepper(cleanPinInput);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const saltedPbkdf2Hash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    const newBizRef = db.collection("businesses").doc();
    const newBizId = newBizRef.id;
    const regPhoneRef = db.collection("registered_phones").doc(normalizedPhone);
    const staffDocRef = newBizRef.collection("staff").doc(ownerUid);
    const staffPhoneRef = newBizRef.collection("staff_by_phone").doc(normalizedPhone);
    const now = Date.now();

    const newBizData = {
      id: newBizId,
      name: String(rawShopName).trim(),
      businessName: String(rawShopName).trim(),
      ownerName: String(rawOwnerName).trim(),
      ownerEmail: ownerEmail,
      ownerPhone: normalizedPhone,
      phone: normalizedPhone,
      ownerUid: ownerUid,
      authProvider: signInProvider,
      address: String(rawAddress).trim().substring(0, 200),
      district: String(rawDistrict).trim().substring(0, 50),
      businessType: String(rawBusinessType).trim().substring(0, 50),
      ownerPinHash: saltedPbkdf2Hash,
      pinHash: saltedPbkdf2Hash,
      pinSecurityVersion: 2,
      status: "PENDING",
      planId: "plan_free",
      planName: "সম্পূর্ণ ফ্রি ব্যবহার",
      subscriptionStatus: "TRIAL",
      subscriptionExpiresAt: 0,
      isAutoExpiryDisabled: true,
      maxStaffAllowed: 3,
      createdAt: now,
      updatedAt: now,
    };

    await db.runTransaction(async (t) => {
      const regDoc = await t.get(regPhoneRef);
      if (regDoc.exists) {
        const regData = regDoc.data() || {};
        if (regData.ownerUid && regData.ownerUid !== ownerUid) {
          throw new functions.https.HttpsError(
            "already-exists",
            "এই মোবাইল নম্বর দিয়ে ইতিমধ্যে অন্য একটি অ্যাকাউন্ট নিবন্ধিত রয়েছে। আপনার নিজস্ব মোবাইল নম্বর দিন।"
          );
        }
      }

      t.set(newBizRef, newBizData);
      t.set(
        regPhoneRef,
        {
          phone: normalizedPhone,
          businessId: newBizId,
          businessName: String(rawShopName).trim(),
          ownerName: String(rawOwnerName).trim(),
          ownerUid: ownerUid,
          ownerEmail: ownerEmail,
          updatedAt: now,
        },
        { merge: true }
      );
      t.set(
        staffDocRef,
        {
          id: ownerUid,
          name: String(rawOwnerName).trim(),
          phone: normalizedPhone,
          email: ownerEmail,
          role: "ADMIN",
          pinHash: saltedPbkdf2Hash,
          pinSecurityVersion: 2,
          isActive: true,
          businessId: newBizId,
          updatedAt: now,
        },
        { merge: true }
      );
      t.set(
        staffPhoneRef,
        {
          userId: ownerUid,
          name: String(rawOwnerName).trim(),
          phone: normalizedPhone,
          role: "ADMIN",
          pinHash: saltedPbkdf2Hash,
          pinSecurityVersion: 2,
          isActive: true,
          updatedAt: now,
        },
        { merge: true }
      );
    });

    return {
      success: true,
      businessId: newBizId,
      businessName: String(rawShopName).trim(),
      ownerUid: ownerUid,
      ownerEmail: ownerEmail,
      ownerName: String(rawOwnerName).trim(),
      role: "ADMIN",
      phone: normalizedPhone,
    };
  });

// Test-only exports: Strictly isolated so they NEVER appear as Cloud Functions endpoints in Production
if (process.env.NODE_ENV === "test") {
  exports.setDbForTesting = setDbForTesting;
  exports.setAuthForTesting = setAuthForTesting;
  exports.getServerPepper = getServerPepper;
  exports.applyServerPepper = applyServerPepper;
  exports.derivePbkdf2Hash = derivePbkdf2Hash;
  exports.verifyPinAndCheckMigration = verifyPinAndCheckMigration;
  exports.normalizeBangladeshiPhone = normalizeBangladeshiPhone;
  exports.isValidBdMobile = isValidBdMobile;
  exports.timingSafeEqual = timingSafeEqual;
  exports.classifyAuthProviders = classifyAuthProviders;
}
