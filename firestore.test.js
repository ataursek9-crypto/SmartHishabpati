const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");

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

