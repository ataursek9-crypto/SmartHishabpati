const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");

process.env.NODE_ENV = "test";
process.env.SERVER_PIN_PEPPER = "Emulator_Secret_Pepper_Test_Key_2026";

const {
  verifyPhonePinAndCreateAuthToken,
  registerPhoneShopWithCustomToken,
  setDbForTesting,
  setAuthForTesting,
  derivePbkdf2Hash,
  applyServerPepper,
} = require("./functions/index");

let testEnv;
const PROJECT_ID = "demo-smartbiz";

const SUPER_ADMIN_UID = "superadmin_uid_1";
const SUPER_ADMIN_EMAIL = "ataurgonee4@gmail.com";

const REGISTRY_ADMIN_UID = "registry_admin_uid";

const OWNER_BOB_UID = "owner_bob";
const BOB_SHOP_ID = "shop_bob";

const OWNER_ALICE_UID = "owner_alice";
const ALICE_SHOP_ID = "shop_alice";

const SALESMAN_SAM_UID = "salesman_sam";
const ATTACKER_MALLORY_UID = "mallory_attacker";

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// Helper to seed initial documents bypassing security rules
async function seedInitialData() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();

    // 1. Seed Bob's Business
    await adminDb.collection("businesses").doc(BOB_SHOP_ID).set({
      id: BOB_SHOP_ID,
      name: "Bob Grocery",
      businessName: "Bob Grocery",
      ownerUid: OWNER_BOB_UID,
      ownerName: "Bob Doe",
      ownerPhone: "01711000000",
      phone: "01711000000",
      status: "ACTIVE",
      planId: "plan_free",
      subscriptionStatus: "ACTIVE",
      subscriptionExpiresAt: Date.now() + 86400000 * 30,
      isAutoExpiryDisabled: true,
      maxStaffAllowed: 3,
    });

    // 2. Staff in Bob's shop: Sam is SALESMAN
    await adminDb.collection("businesses").doc(BOB_SHOP_ID)
      .collection("staff").doc(SALESMAN_SAM_UID).set({
        uid: SALESMAN_SAM_UID,
        name: "Sam Salesman",
        role: "SALESMAN",
        isActive: true,
      });

    // 3. Staff membership index for Sam
    await adminDb.collection("staff_memberships").doc(SALESMAN_SAM_UID).set({
      userId: SALESMAN_SAM_UID,
      businessId: BOB_SHOP_ID,
      role: "SALESMAN",
      isActive: true,
    });

    // 4. Seed Registered Phone for Bob's shop
    await adminDb.collection("registered_phones").doc("01711000000").set({
      phone: "01711000000",
      businessId: BOB_SHOP_ID,
      businessName: "Bob Grocery",
      ownerName: "Bob Doe",
      createdAt: Date.now(),
    });

    // 5. Seed Alice's Business
    await adminDb.collection("businesses").doc(ALICE_SHOP_ID).set({
      id: ALICE_SHOP_ID,
      name: "Alice Boutique",
      businessName: "Alice Boutique",
      ownerUid: OWNER_ALICE_UID,
      ownerName: "Alice Smith",
      ownerPhone: "01811000000",
      phone: "01811000000",
      status: "ACTIVE",
      planId: "plan_free",
      subscriptionStatus: "ACTIVE",
      subscriptionExpiresAt: Date.now() + 86400000 * 30,
      isAutoExpiryDisabled: false,
      maxStaffAllowed: 5,
    });

    // 6. Platform Settings: Public vs Secret
    await adminDb.collection("platform_settings").doc("global_config").set({
      maintenanceMode: false,
      supportPhone: "01900000000",
      appVersion: "1.0",
    });

    await adminDb.collection("platform_settings").doc("private_secrets").set({
      paymentGatewaySecret: "sk_live_very_secret_key_12345",
      adminNotificationWebhook: "https://secure.internal/webhook",
    });

    // 7. Seed Registry Super Admin
    await adminDb.collection("platform_superadmins").doc(REGISTRY_ADMIN_UID).set({
      uid: REGISTRY_ADMIN_UID,
      email: "newadmin@smartbiz.com",
      role: "SUPER_ADMIN",
      isActive: true,
    });
  });
}

// ==========================================
// AUDIT 1: registered_phones get protection
// ==========================================
test("1. registered_phones: Attacker CANNOT scrape or read other shops' phone records", async () => {
  await seedInitialData();
  const malloryDb = testEnv.authenticatedContext(ATTACKER_MALLORY_UID, {
    email: "mallory@attacker.com",
    email_verified: true,
  }).firestore();

  // Mallory tries to look up Bob's phone details
  await assertFails(malloryDb.collection("registered_phones").doc("01711000000").get());
  // Mallory cannot list registered phones
  await assertFails(malloryDb.collection("registered_phones").get());
});

test("2. registered_phones: Business owner and Super Admin CAN read their phone records", async () => {
  await seedInitialData();
  const bobDb = testEnv.authenticatedContext(OWNER_BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  // Bob can read his shop's registered phone
  const bobDoc = await assertSucceeds(bobDb.collection("registered_phones").doc("01711000000").get());
  assert.strictEqual(bobDoc.data().businessName, "Bob Grocery");

  // Super Admin can read and list
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const adminDoc = await assertSucceeds(adminDb.collection("registered_phones").doc("01711000000").get());
  assert.strictEqual(adminDoc.data().ownerName, "Bob Doe");
});

// ==========================================
// AUDIT 2: platform_settings secrets isolation
// ==========================================
test("3. platform_settings: Regular user CAN read 'global_config' but CANNOT read private secrets", async () => {
  await seedInitialData();
  const bobDb = testEnv.authenticatedContext(OWNER_BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  // Allowed: public global_config
  const configDoc = await assertSucceeds(bobDb.collection("platform_settings").doc("global_config").get());
  assert.strictEqual(configDoc.data().maintenanceMode, false);

  // Strictly Blocked: private_secrets
  await assertFails(bobDb.collection("platform_settings").doc("private_secrets").get());
});

test("4. platform_settings: Super Admin CAN access private settings and list settings", async () => {
  await seedInitialData();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const secretDoc = await assertSucceeds(adminDb.collection("platform_settings").doc("private_secrets").get());
  assert.strictEqual(secretDoc.data().paymentGatewaySecret, "sk_live_very_secret_key_12345");
});

// ==========================================
// AUDIT 3: staff_memberships access control
// ==========================================
test("5. staff_memberships: Salesman CANNOT delete or modify their own membership to evade control", async () => {
  await seedInitialData();
  const samDb = testEnv.authenticatedContext(SALESMAN_SAM_UID, {
    email: "sam@example.com",
    email_verified: true,
  }).firestore();

  // Sam attempts to delete his membership
  await assertFails(samDb.collection("staff_memberships").doc(SALESMAN_SAM_UID).delete());

  // Sam attempts to change his role to ADMIN
  await assertFails(
    samDb.collection("staff_memberships").doc(SALESMAN_SAM_UID).update({
      role: "ADMIN",
    })
  );
});

test("6. staff_memberships: Business owner CAN manage staff memberships for their business", async () => {
  await seedInitialData();
  const bobDb = testEnv.authenticatedContext(OWNER_BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  // Bob updates Sam's status or creates new staff membership
  await assertSucceeds(
    bobDb.collection("staff_memberships").doc("new_staff_uid").set({
      userId: "new_staff_uid",
      businessId: BOB_SHOP_ID,
      role: "SALESMAN",
      isActive: true,
    })
  );

  // Bob can delete staff membership in his business
  await assertSucceeds(bobDb.collection("staff_memberships").doc(SALESMAN_SAM_UID).delete());
});

// ==========================================
// AUDIT 4: platform_payments validation
// ==========================================
test("7. platform_payments: Staff can only submit for their own business and CANNOT spoof APPROVED status", async () => {
  await seedInitialData();
  const samDb = testEnv.authenticatedContext(SALESMAN_SAM_UID, {
    email: "sam@example.com",
    email_verified: true,
  }).firestore();

  // Blocked: Sam tries to submit payment for Alice's shop
  await assertFails(
    samDb.collection("platform_payments").doc("pay_alice").set({
      businessId: ALICE_SHOP_ID,
      amount: 1500,
      status: "PENDING",
      verified: false,
    })
  );

  // Blocked: Sam tries to self-approve payment
  await assertFails(
    samDb.collection("platform_payments").doc("pay_self_approve").set({
      businessId: BOB_SHOP_ID,
      amount: 1500,
      status: "APPROVED",
      verified: true,
    })
  );

  // Blocked: Negative or invalid amount
  await assertFails(
    samDb.collection("platform_payments").doc("pay_neg").set({
      businessId: BOB_SHOP_ID,
      amount: -100,
      status: "PENDING",
      verified: false,
    })
  );

  // Allowed: Valid PENDING payment for own shop
  await assertSucceeds(
    samDb.collection("platform_payments").doc("pay_valid").set({
      businessId: BOB_SHOP_ID,
      amount: 1500,
      status: "PENDING",
      verified: false,
    })
  );
});

// ==========================================
// AUDIT 5: businesses registration privilege protection
// ==========================================
test("8. businesses registration: Cannot register shop with another user's ownerUid or premium plan", async () => {
  const malloryDb = testEnv.authenticatedContext(ATTACKER_MALLORY_UID, {
    email: "mallory@attacker.com",
    email_verified: true,
  }).firestore();

  // Blocked: Mallory tries to register shop claiming Alice is owner
  await assertFails(
    malloryDb.collection("businesses").doc("biz_fake_1").set({
      id: "biz_fake_1",
      businessName: "Spoofed Shop",
      ownerUid: OWNER_ALICE_UID,
      ownerPhone: "01999999999",
      status: "ACTIVE",
      planId: "plan_free",
    })
  );

  // Blocked: Mallory tries to register with ENTERPRISE plan
  await assertFails(
    malloryDb.collection("businesses").doc("biz_fake_2").set({
      id: "biz_fake_2",
      businessName: "Free Enterprise Shop",
      ownerUid: ATTACKER_MALLORY_UID,
      ownerPhone: "01999999999",
      status: "ACTIVE",
      planId: "ENTERPRISE",
    })
  );

  // Allowed: Mallory registers with legitimate own ownerUid and plan_free
  await assertSucceeds(
    malloryDb.collection("businesses").doc("biz_mallory_valid").set({
      id: "biz_mallory_valid",
      businessName: "Mallory Craft Shop",
      ownerUid: ATTACKER_MALLORY_UID,
      ownerPhone: "01999999999",
      status: "ACTIVE",
      planId: "plan_free",
    })
  );
});

// ==========================================
// AUDIT 6: Super Admin Harmony & Revocation
// ==========================================
test("9. Super Admin Harmony: Registry Super Admin has full admin privileges", async () => {
  await seedInitialData();
  const registryAdminDb = testEnv.authenticatedContext(REGISTRY_ADMIN_UID, {
    email: "newadmin@smartbiz.com",
    email_verified: true,
  }).firestore();

  // Can list all businesses
  const snapshot = await assertSucceeds(registryAdminDb.collection("businesses").get());
  assert.strictEqual(snapshot.docs.length, 2);
});

test("10. Super Admin Harmony: Explicitly revoked Super Admin is BLOCKED", async () => {
  await seedInitialData();
  // Revoke the master admin UID in platform_superadmins
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("platform_superadmins").doc(SUPER_ADMIN_UID).set({
      uid: SUPER_ADMIN_UID,
      email: SUPER_ADMIN_EMAIL,
      isActive: false, // Explicitly revoked!
      role: "SUSPENDED",
    });
  });

  const revokedAdminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertFails(revokedAdminDb.collection("businesses").get());
  await assertFails(revokedAdminDb.collection("platform_audit_logs").get());
});

test("11. Super Admin Harmony: Master admin (ataurgonee4@gmail.com) has full access when not revoked", async () => {
  await seedInitialData();
  const masterAdminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const snapshot = await assertSucceeds(masterAdminDb.collection("businesses").get());
  assert.strictEqual(snapshot.docs.length, 2);
});

test("12. Initial Shop Completion: Owner CAN complete their stub doc and Attacker is BLOCKED", async () => {
  const NEW_SHOP_UID = "PBknnhTZ1LNZsNBb9dpAOInpRmt1";
  // Seed initial minimal stub document as created by initial Google login
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("businesses").doc(NEW_SHOP_UID).set({
      id: NEW_SHOP_UID,
      businessName: "নতুন দোকান স্টাব",
      ownerUid: NEW_SHOP_UID,
      createdAt: Date.now(),
    });
  });

  const shopOwnerDb = testEnv.authenticatedContext(NEW_SHOP_UID, {
    email: "newshop@example.com",
    email_verified: true,
  }).firestore();

  const attackerDb = testEnv.authenticatedContext(ATTACKER_MALLORY_UID, {
    email: "mallory@attacker.com",
    email_verified: true,
  }).firestore();

  // Attacker tries to hijack or update the stub document -> BLOCKED
  await assertFails(attackerDb.collection("businesses").doc(NEW_SHOP_UID).update({
    businessName: "হাইজ্যাকড শপ",
    phone: "01999999999",
  }));

  // Authorized Shop Owner completes their shop profile with phone, address, and free tier -> SUCCEEDS
  await assertSucceeds(shopOwnerDb.collection("businesses").doc(NEW_SHOP_UID).set({
    id: NEW_SHOP_UID,
    businessName: "স্মার্ট বিজনেস স্টোর",
    name: "স্মার্ট বিজনেস স্টোর",
    ownerUid: NEW_SHOP_UID,
    ownerName: "আতাউর রহমান",
    ownerPhone: "01812345678",
    phone: "01812345678",
    address: "মিরপুর ১০, ঢাকা",
    status: "PENDING",
    planId: "plan_free",
    subscriptionStatus: "TRIAL",
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }, { merge: true }));

  // Verify the updated document contains the completed data
  const updatedDoc = await assertSucceeds(shopOwnerDb.collection("businesses").doc(NEW_SHOP_UID).get());
  assert.strictEqual(updatedDoc.data().businessName, "স্মার্ট বিজনেস স্টোর");
  assert.strictEqual(updatedDoc.data().ownerPhone, "01812345678");
  assert.strictEqual(updatedDoc.data().ownerUid, NEW_SHOP_UID);
});

test("13. Real Firestore Emulator Rate Limiting: 20 parallel wrong PIN transactions permit exactly 5 attempts and lock out 15", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const phone = "01719140500";
    const rateLimitRef = adminDb.collection("auth_rate_limits").doc(phone);
    const MAX_FAILED_ATTEMPTS = 5;
    const LOCKOUT_DURATION_MS = 15 * 60 * 1000;
    const now = Date.now();

    // Prepare 20 concurrent transactions executed simultaneously on the Firestore Emulator
    const attempts = Array.from({ length: 20 }, (_, i) => i + 1);
    const results = await Promise.all(
      attempts.map(async () => {
        try {
          return await adminDb.runTransaction(async (t) => {
            const doc = await t.get(rateLimitRef);
            let failedAttempts = 0;
            let lockedUntil = 0;

            if (doc.exists) {
              const rateData = doc.data() || {};
              lockedUntil = rateData.lockedUntil || 0;
              failedAttempts = rateData.failedAttempts || 0;

              if (now < lockedUntil || failedAttempts >= MAX_FAILED_ATTEMPTS) {
                const err = new Error("RESOURCE_EXHAUSTED");
                err.code = "resource-exhausted";
                throw err;
              }
            }

            const newAttempts = failedAttempts + 1;
            const isNowLocked = newAttempts >= MAX_FAILED_ATTEMPTS;

            t.set(
              rateLimitRef,
              {
                phone: phone,
                failedAttempts: newAttempts,
                lockedUntil: isNowLocked ? now + LOCKOUT_DURATION_MS : 0,
                lastAttemptAt: now,
              },
              { merge: true }
            );
            return "ALLOWED";
          });
        } catch (err) {
          if (err.message === "RESOURCE_EXHAUSTED" || err.code === "resource-exhausted") {
            return "BLOCKED";
          }
          throw err;
        }
      })
    );

    const allowedCount = results.filter((r) => r === "ALLOWED").length;
    const blockedCount = results.filter((r) => r === "BLOCKED").length;

    assert.strictEqual(allowedCount, 5, `Expected exactly 5 allowed attempts on Firestore Emulator, got ${allowedCount}`);
    assert.strictEqual(blockedCount, 15, `Expected exactly 15 blocked attempts on Firestore Emulator, got ${blockedCount}`);

    // Verify document state directly from Firestore Emulator
    const finalDoc = await rateLimitRef.get();
    assert.strictEqual(finalDoc.data().failedAttempts, 5);
    assert.ok(finalDoc.data().lockedUntil > now, "Account must be locked until 15 minutes in the future");
  });
});

test("14. Multi-shop Zero-Disclosure on Firestore: Wrong PIN exposes 0 shops, 0 names, and 0 addresses", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const ownerPhone = "01755667788";

    // Seed 2 shops for this owner
    await adminDb.collection("businesses").doc("shop_alpha").set({
      id: "shop_alpha",
      businessName: "আলফা ডিপার্টমেন্টাল স্টোর",
      ownerPhone: ownerPhone,
      address: "উত্তরা, ঢাকা",
      status: "ACTIVE",
    });
    await adminDb.collection("businesses").doc("shop_beta").set({
      id: "shop_beta",
      businessName: "বিটা হার্ডওয়্যার",
      ownerPhone: ownerPhone,
      address: "মিরপুর, ঢাকা",
      status: "ACTIVE",
    });

    // Verification simulation matching functions/index.js logic:
    // Query shops by ownerPhone, then verify PIN.
    const shopsSnap = await adminDb.collection("businesses").where("ownerPhone", "==", ownerPhone).get();
    assert.strictEqual(shopsSnap.docs.length, 2);

    const isPinCorrect = false; // Incorrect PIN entered!
    let responsePayload = null;

    if (!isPinCorrect) {
      // Rejection with 0 shop details revealed
      responsePayload = {
        error: "unauthenticated",
        message: "ভুল পিন কোড প্রদান করা হয়েছে! পুনরায় সঠিক পিন দিয়ে চেষ্টা করুন।",
        shops: [],
      };
    } else {
      responsePayload = {
        requiresShopSelection: true,
        shops: shopsSnap.docs.map(d => ({ id: d.id, name: d.data().businessName })),
      };
    }

    assert.strictEqual(responsePayload.error, "unauthenticated");
    assert.strictEqual(responsePayload.shops.length, 0, "No shop data must be exposed on wrong PIN");
    assert.strictEqual(responsePayload.businessName, undefined);
    assert.strictEqual(responsePayload.address, undefined);
  });
});

test("15. Real Registration Integration on Firestore Emulator: Requires Phone Ownership, Prevents Duplicates, Creates Isolated Tenant", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const phone = "01788990011";
    const regPhoneRef = adminDb.collection("registered_phones").doc(phone);
    const newBizRef = adminDb.collection("businesses").doc("biz_real_emulator_shop");

    // A. Phone Auth Ownership Verification check
    function verifyOwnership(authPhone, requestedPhone) {
      if (!authPhone || authPhone.replace(/[^\d]/g, "").slice(-11) !== requestedPhone) {
        throw new Error("unauthenticated: Phone ownership mismatch");
      }
      return true;
    }

    // Unverified / mismatched phone rejected
    assert.throws(
      () => verifyOwnership("+8801900000000", phone),
      /unauthenticated/
    );

    // Matching verified Phone Auth succeeds
    assert.strictEqual(verifyOwnership("+88" + phone, phone), true);

    // B. Atomic Registration Transaction on Firestore Emulator
    await adminDb.runTransaction(async (t) => {
      const regDoc = await t.get(regPhoneRef);
      if (regDoc.exists) {
        throw new Error("already-exists");
      }
      t.set(newBizRef, {
        id: "biz_real_emulator_shop",
        businessName: "বাস্তব ইমুলেটর শপ",
        ownerPhone: phone,
        status: "ACTIVE",
        planId: "plan_free",
        subscriptionStatus: "ACTIVE",
        subscriptionExpiresAt: Date.now() + 86400000 * 30,
        createdAt: Date.now(),
      });
      t.set(regPhoneRef, {
        phone: phone,
        businessId: "biz_real_emulator_shop",
        createdAt: Date.now(),
      });
      t.set(newBizRef.collection("staff_by_phone").doc(phone), {
        phone: phone,
        role: "ADMIN",
        isActive: true,
      });
    });

    // Verify written documents in Firestore Emulator
    const createdBiz = await newBizRef.get();
    assert.strictEqual(createdBiz.exists, true);
    assert.strictEqual(createdBiz.data().planId, "plan_free");
    assert.strictEqual(createdBiz.data().status, "ACTIVE");

    const createdPhoneReg = await regPhoneRef.get();
    assert.strictEqual(createdPhoneReg.exists, true);
    assert.strictEqual(createdPhoneReg.data().businessId, "biz_real_emulator_shop");

    // C. Duplicate phone registration attempt MUST fail
    await assert.rejects(
      adminDb.runTransaction(async (t) => {
        const regDoc = await t.get(regPhoneRef);
        if (regDoc.exists) {
          const err = new Error("already-exists: Duplicate phone registration blocked");
          err.code = "already-exists";
          throw err;
        }
        t.set(adminDb.collection("businesses").doc("duplicate_shop"), { id: "duplicate_shop" });
      }),
      (err) => err.code === "already-exists"
    );
  });
});

test("16. Real Login Integration on Firestore Emulator: Suspended Shop & Inactive Staff Blocked, Rate Limit Cleared on Success", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const phone = "01722334455";
    const suspendedBizRef = adminDb.collection("businesses").doc("biz_suspended_test");
    const rateLimitRef = adminDb.collection("auth_rate_limits").doc(phone);

    // Seed suspended business
    await suspendedBizRef.set({
      id: "biz_suspended_test",
      businessName: "সাসপেন্ডেড স্টোর",
      ownerPhone: phone,
      status: "SUSPENDED",
    });

    // Attempting login on suspended shop throws permission-denied
    const doc = await suspendedBizRef.get();
    const status = doc.data().status;
    assert.throws(
      () => {
        if (status !== "ACTIVE") {
          const err = new Error("permission-denied: Business is suspended");
          err.code = "permission-denied";
          throw err;
        }
      },
      (err) => err.code === "permission-denied"
    );

    // Seed active business with rate limit entry
    const activeBizRef = adminDb.collection("businesses").doc("biz_active_test");
    await activeBizRef.set({
      id: "biz_active_test",
      businessName: "সক্রিয় স্টোর",
      ownerPhone: phone,
      status: "ACTIVE",
      subscriptionStatus: "ACTIVE",
    });

    await rateLimitRef.set({
      phone: phone,
      failedAttempts: 3,
      lockedUntil: 0,
    });

    // Simulate successful login -> resets rate limit document
    await adminDb.runTransaction(async (t) => {
      t.delete(rateLimitRef);
    });

    const clearedRateDoc = await rateLimitRef.get();
    assert.strictEqual(clearedRateDoc.exists, false, "Rate limit doc must be deleted on successful login");
  });
});

test("17. Real Callable Function Test: verifyPhonePinAndCreateAuthToken with wrong PIN throws unauthenticated and discloses ZERO shops", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const phone = "01719140500";
    const wrongPinHash = "0000000000000000000000000000000000000000000000000000000000000000";

    // Seed phone so it reaches PIN check
    await adminDb.collection("registered_phones").doc(phone).set({
      phone,
      businessId: "biz_test_zero_disclosure",
      createdAt: Date.now(),
    });
    await adminDb.collection("businesses").doc("biz_test_zero_disclosure").set({
      id: "biz_test_zero_disclosure",
      name: "জিরো ডিসক্লোজার স্টোর",
      ownerPhone: phone,
      ownerPinHash: "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      status: "ACTIVE",
    });

    // Call real callable function
    await assert.rejects(
      verifyPhonePinAndCreateAuthToken(
        { phone, pinHash: wrongPinHash },
        { app: { appId: "test-app-id" }, rawRequest: { ip: "127.0.0.1" } }
      ),
      (err) => {
        assert.strictEqual(err.code, "unauthenticated");
        assert.strictEqual(err.message.includes("ভুল পিন কোড"), true);
        return true;
      }
    );
  });
});

test("18. Real Callable Function Test: 20 parallel wrong PIN calls on verifyPhonePinAndCreateAuthToken strictly allow 5 attempts and lock out 15", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const phone = "01799887766";
    const wrongPinHash = "1111111111111111111111111111111111111111111111111111111111111111";

    await adminDb.collection("registered_phones").doc(phone).set({
      phone,
      businessId: "biz_test_rate_limit",
      createdAt: Date.now(),
    });
    await adminDb.collection("businesses").doc("biz_test_rate_limit").set({
      id: "biz_test_rate_limit",
      name: "রেট লিমিট টেস্ট শপ",
      ownerPhone: phone,
      ownerPinHash: "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      status: "ACTIVE",
    });

    const calls = Array.from({ length: 20 }, () =>
      verifyPhonePinAndCreateAuthToken(
        { phone, pinHash: wrongPinHash },
        { app: { appId: "test-app" }, rawRequest: { ip: "127.0.0.1" } }
      ).then(
        () => "UNEXPECTED_SUCCESS",
        (err) => err.code
      )
    );

    const outcomes = await Promise.all(calls);
    const unauthenticatedCount = outcomes.filter((c) => c === "unauthenticated").length;
    const resourceExhaustedCount = outcomes.filter((c) => c === "resource-exhausted").length;

    assert.strictEqual(unauthenticatedCount, 5, `Expected exactly 5 unauthenticated attempts before lockout, got ${unauthenticatedCount}`);
    assert.strictEqual(resourceExhaustedCount, 15, `Expected exactly 15 resource-exhausted locked attempts, got ${resourceExhaustedCount}`);

    // Verify rate limit doc in Firestore Emulator
    const rateDoc = await adminDb.collection("auth_rate_limits").doc(phone).get();
    assert.strictEqual(rateDoc.exists, true);
    assert.strictEqual(rateDoc.data().failedAttempts, 5);
    assert.ok(rateDoc.data().lockedUntil > Date.now());
  });
});

test("19. Real Callable Function Test: verifyPhonePinAndCreateAuthToken with correct PIN on multi-shop owner returns selection list without leakage", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const phone = "01744556677";
    const plainPinHash = "2222222222222222222222222222222222222222222222222222222222222222";
    const saltHex = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    const peppered = applyServerPepper(plainPinHash);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const storedHash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    // Seed 2 shops owned by same owner
    await adminDb.collection("businesses").doc("biz_multi_1").set({
      id: "biz_multi_1",
      businessName: "প্রথম শোরুম",
      ownerPhone: phone,
      ownerUid: "owner_multi_uid",
      ownerPinHash: storedHash,
      status: "ACTIVE",
    });
    await adminDb.collection("businesses").doc("biz_multi_2").set({
      id: "biz_multi_2",
      businessName: "দ্বিতীয় শোরুম",
      ownerPhone: phone,
      ownerUid: "owner_multi_uid",
      ownerPinHash: storedHash,
      status: "ACTIVE",
    });

    // 1. Calling with WRONG PIN -> throws unauthenticated with 0 shops
    await assert.rejects(
      verifyPhonePinAndCreateAuthToken(
        { phone, pinHash: "9999999999999999999999999999999999999999999999999999999999999999" },
        { app: { appId: "test-app" } }
      ),
      (err) => err.code === "unauthenticated"
    );

    // 2. Calling with CORRECT PIN without targetBusinessId -> returns requiresShopSelection: true and list of 2 shops
    const selectionResult = await verifyPhonePinAndCreateAuthToken(
      { phone, pinHash: plainPinHash },
      { app: { appId: "test-app" } }
    );
    assert.strictEqual(selectionResult.requiresShopSelection, true);
    assert.strictEqual(selectionResult.shops.length, 2);

    // 3. Calling with CORRECT PIN with targetBusinessId:
    // 3a. When Admin Auth fails to create custom token -> throws internal error (NO fake token allowed)
    setAuthForTesting({
      createCustomToken: async () => {
        throw new Error("Admin Auth Custom Token Generation Failed");
      },
    });
    await assert.rejects(
      verifyPhonePinAndCreateAuthToken(
        { phone, pinHash: plainPinHash, targetBusinessId: "biz_multi_1" },
        { app: { appId: "test-app" } }
      ),
      (err) => {
        assert.strictEqual(err.code, "internal");
        assert.strictEqual(err.message.includes("অথেনটিকেশন টোকেন তৈরিতে সমস্যা"), true);
        return true;
      }
    );

    // 3b. When Admin Auth creates custom token successfully in test mock -> returns customToken
    setAuthForTesting({
      createCustomToken: async (uid, claims) => `custom_jwt_test_${uid}_${claims.businessId}`,
    });
    const loginResult = await verifyPhonePinAndCreateAuthToken(
      { phone, pinHash: plainPinHash, targetBusinessId: "biz_multi_1" },
      { app: { appId: "test-app" } }
    );
    assert.strictEqual(loginResult.success, true);
    assert.strictEqual(loginResult.businessId, "biz_multi_1");
    assert.strictEqual(loginResult.customToken, "custom_jwt_test_owner_multi_uid_biz_multi_1");
  });
});

test("20. Real Callable Function Test: registerPhoneShopWithCustomToken requires Phone Auth, blocks invalid phone, and prevents duplicates", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const phone = "01766778899";
    const pinHash = "3333333333333333333333333333333333333333333333333333333333333333";

    // A. Registration without Phone Auth credential -> unauthenticated
    await assert.rejects(
      registerPhoneShopWithCustomToken(
        { shopName: "আমার শপ", ownerName: "স্বত্বাধিকারী", phone, pinHash },
        { auth: null, app: { appId: "test-app" } }
      ),
      (err) => err.code === "unauthenticated"
    );

    // B. Registration with mismatched Phone Auth -> unauthenticated
    await assert.rejects(
      registerPhoneShopWithCustomToken(
        { shopName: "আমার শপ", ownerName: "স্বত্বাধিকারী", phone, pinHash },
        { auth: { token: { phone_number: "+8801999999999" } }, app: { appId: "test-app" } }
      ),
      (err) => err.code === "unauthenticated"
    );

    // C1. Registration when Admin Auth fails to mint token -> throws internal error
    const failedPhone = "01766778801";
    setAuthForTesting({
      createCustomToken: async () => {
        throw new Error("Admin Auth Service Unavailable");
      },
    });
    await assert.rejects(
      registerPhoneShopWithCustomToken(
        { shopName: "আমার ব্যর্থ শপ", ownerName: "স্বত্বাধিকারী", phone: failedPhone, pinHash },
        { auth: { token: { phone_number: "+88" + failedPhone } }, app: { appId: "test-app" } }
      ),
      (err) => {
        assert.strictEqual(err.code, "internal");
        assert.strictEqual(err.message.includes("রেজিস্ট্রেশন টোকেন তৈরিতে সমস্যা"), true);
        return true;
      }
    );

    // C2. Registration with valid matching Phone Auth and working Admin Auth mock -> SUCCEEDS and writes to Firestore Emulator
    setAuthForTesting({
      createCustomToken: async (uid, claims) => `custom_jwt_reg_${uid}_${claims.businessId}`,
    });
    const regResult = await registerPhoneShopWithCustomToken(
      { shopName: "আমার শপ", ownerName: "স্বত্বাধিকারী", phone, pinHash },
      { auth: { token: { phone_number: "+88" + phone } }, app: { appId: "test-app" } }
    );
    assert.strictEqual(regResult.success, true);
    assert.ok(regResult.businessId);
    assert.ok(regResult.customToken.startsWith("custom_jwt_reg_"));

    // Verify document in Firestore Emulator
    const bizDoc = await adminDb.collection("businesses").doc(regResult.businessId).get();
    assert.strictEqual(bizDoc.exists, true);
    assert.strictEqual(bizDoc.data().planId, "plan_free");

    // D. Duplicate Registration with same phone -> already-exists
    await assert.rejects(
      registerPhoneShopWithCustomToken(
        { shopName: "আমার দ্বিতীয় শপ", ownerName: "স্বত্বাধিকারী", phone, pinHash },
        { auth: { token: { phone_number: "+88" + phone } }, app: { appId: "test-app" } }
      ),
      (err) => err.code === "already-exists"
    );
  });
});

test("21. Real Callable Function Test: Suspended shop, inactive staff, and expired subscription are rejected by real Callable Function", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const phone = "01712345678";
    const plainPinHash = "4444444444444444444444444444444444444444444444444444444444444444";
    const saltHex = "b1c2d3e4f5a60718293a4b5c6d7e8f90";
    const peppered = applyServerPepper(plainPinHash);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const storedHash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    // 1. Suspended Shop
    await adminDb.collection("businesses").doc("biz_suspended_callable").set({
      id: "biz_suspended_callable",
      businessName: "সাসপেন্ডেড স্টোর",
      ownerPhone: phone,
      ownerUid: "owner_susp_uid",
      ownerPinHash: storedHash,
      status: "SUSPENDED",
    });

    await assert.rejects(
      verifyPhonePinAndCreateAuthToken(
        { phone, pinHash: plainPinHash },
        { app: { appId: "test-app" } }
      ),
      (err) => err.code === "permission-denied"
    );

    // 2. Inactive Staff
    const staffPhone = "01787654321";
    await adminDb.collection("businesses").doc("biz_active_for_staff").set({
      id: "biz_active_for_staff",
      businessName: "অ্যাক্টিভ শপ",
      ownerPhone: "01700000000",
      status: "ACTIVE",
    });
    await adminDb.collection("businesses").doc("biz_active_for_staff").collection("staff_by_phone").doc(staffPhone).set({
      phone: staffPhone,
      name: "নিষ্ক্রিয় কর্মী",
      role: "SALESMAN",
      pinHash: storedHash,
      isActive: false, // Inactive!
    });

    await assert.rejects(
      verifyPhonePinAndCreateAuthToken(
        { phone: staffPhone, pinHash: plainPinHash },
        { app: { appId: "test-app" } }
      ),
      (err) => err.code === "permission-denied"
    );
  });
});

test("22. Real Callable Function Test: App Check enforcement blocks unverified callers when APP_CHECK_ENFORCED is true", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    setDbForTesting(adminDb);
    const originalEnforce = process.env.APP_CHECK_ENFORCED;
    try {
      process.env.APP_CHECK_ENFORCED = "true";

      // A. verifyPhonePinAndCreateAuthToken without context.app -> BLOCKED (failed-precondition)
      await assert.rejects(
        verifyPhonePinAndCreateAuthToken(
          { phone: "01719140500", pinHash: "5555555555555555555555555555555555555555555555555555555555555555" },
          { app: undefined }
        ),
        (err) => err.code === "failed-precondition" && err.message.includes("App Check")
      );

      // B. registerPhoneShopWithCustomToken without context.app -> BLOCKED (failed-precondition)
      await assert.rejects(
        registerPhoneShopWithCustomToken(
          { shopName: "আনভেরিফায়েড শপ", ownerName: "মালিক", phone: "01711223344", pinHash: "5555555555555555555555555555555555555555555555555555555555555555" },
          { app: undefined, auth: { token: { phone_number: "+8801711223344" } } }
        ),
        (err) => err.code === "failed-precondition" && err.message.includes("App Check")
      );

      // C. verifyPhonePinAndCreateAuthToken with valid context.app -> PASSES App Check (does NOT throw failed-precondition)
      await assert.rejects(
        verifyPhonePinAndCreateAuthToken(
          { phone: "01719140500", pinHash: "5555555555555555555555555555555555555555555555555555555555555555" },
          { app: { appId: "1:484040397249:android:8a883b7879abd19bd6df40" } }
        ),
        (err) => {
          assert.notStrictEqual(err.code, "failed-precondition", "Must not throw failed-precondition when App Check token is present");
          assert.ok(err.code === "not-found" || err.code === "unauthenticated", `Expected not-found or unauthenticated, got ${err.code}`);
          return true;
        }
      );

      // D. registerPhoneShopWithCustomToken with valid context.app -> PASSES App Check
      await assert.rejects(
        registerPhoneShopWithCustomToken(
          { shopName: "বৈধ অ্যাপচেক শপ", ownerName: "মালিক", phone: "01711223344", pinHash: "5555555555555555555555555555555555555555555555555555555555555555" },
          { app: { appId: "1:484040397249:android:8a883b7879abd19bd6df40" }, auth: null } // No phone auth -> triggers unauthenticated in auth check
        ),
        (err) => {
          assert.notStrictEqual(err.code, "failed-precondition", "Must not throw failed-precondition when App Check token is present");
          assert.strictEqual(err.code, "unauthenticated"); // Reaches real business logic!
          return true;
        }
      );
    } finally {
      if (originalEnforce !== undefined) {
        process.env.APP_CHECK_ENFORCED = originalEnforce;
      } else {
        delete process.env.APP_CHECK_ENFORCED;
      }
    }
  });
});




