/**
 * Real Firebase Emulator Security Rules Test Suite
 * Tests live rules against Cloud Firestore Emulator on http://127.0.0.1:8085
 */
const assert = require('node:assert');

const BASE_URL = 'http://127.0.0.1:8085/v1/projects/demo-no-project/databases/(default)/documents';

function makeToken(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const claims = Buffer.from(JSON.stringify({ aud: 'demo-no-project', ...payload })).toString('base64url');
  return `${header}.${claims}.`;
}

// Convert simple JS object to Firestore REST document representation
function toFirestoreDoc(obj) {
  const fields = {};
  for (const [key, val] of Object.entries(obj)) {
    if (typeof val === 'string') fields[key] = { stringValue: val };
    else if (typeof val === 'number') fields[key] = { doubleValue: val };
    else if (typeof val === 'boolean') fields[key] = { booleanValue: val };
    else if (Array.isArray(val)) fields[key] = { arrayValue: { values: val.map(v => ({ stringValue: String(v) })) } };
    else if (typeof val === 'object' && val !== null) fields[key] = { mapValue: toFirestoreDoc(val) };
  }
  return { fields };
}

const SUPER_ADMIN_TOKEN = makeToken({
  sub: 'super_admin_uid',
  user_id: 'super_admin_uid',
  email: 'ataurgonee4@gmail.com',
  email_verified: true
});

const OWNER_A_UID = 'owner_shop_a_uid';
const OWNER_A_TOKEN = makeToken({
  sub: OWNER_A_UID,
  user_id: OWNER_A_UID,
  email: 'owner.a@example.com',
  email_verified: true
});

const OWNER_B_UID = 'owner_shop_b_uid';
const OWNER_B_TOKEN = makeToken({
  sub: OWNER_B_UID,
  user_id: OWNER_B_UID,
  email: 'owner.b@example.com',
  email_verified: true
});

const SALESMAN_A_UID = 'salesman_shop_a_uid';
const SALESMAN_A_TOKEN = makeToken({
  sub: SALESMAN_A_UID,
  user_id: SALESMAN_A_UID,
  email: 'salesman.a@example.com',
  email_verified: true
});

const NORMAL_USER_TOKEN = makeToken({
  sub: 'normal_user_uid',
  user_id: 'normal_user_uid',
  email: 'random.user@example.com',
  email_verified: true
});

const SHOP_A_ID = 'shop_alpha_101';
const SHOP_B_ID = 'shop_beta_202';

async function setupFixtures() {
  // Use Super Admin to seed baseline shops and memberships in emulator
  // 1. Seed Shop A
  await fetch(`${BASE_URL}/businesses?documentId=${SHOP_A_ID}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(toFirestoreDoc({
      id: SHOP_A_ID,
      businessName: 'দোকান আলফা',
      ownerUid: OWNER_A_UID,
      ownerPhone: '01711112222',
      status: 'ACTIVE'
    }))
  });

  // 2. Seed Shop B
  await fetch(`${BASE_URL}/businesses?documentId=${SHOP_B_ID}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(toFirestoreDoc({
      id: SHOP_B_ID,
      businessName: 'দোকান বিটা',
      ownerUid: OWNER_B_UID,
      ownerPhone: '01733334444',
      status: 'ACTIVE'
    }))
  });

  // 3. Seed Staff Membership for Salesman A in Shop A
  await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/staff?documentId=${SALESMAN_A_UID}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(toFirestoreDoc({
      uid: SALESMAN_A_UID,
      name: 'করিম সেলসম্যান',
      role: 'SALESMAN',
      isActive: true,
      shopId: SHOP_A_ID
    }))
  });

  await fetch(`${BASE_URL}/staff_memberships?documentId=${SALESMAN_A_UID}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(toFirestoreDoc({
      userId: SALESMAN_A_UID,
      businessId: SHOP_A_ID,
      role: 'SALESMAN',
      isActive: true
    }))
  });

  // 4. Seed Shop A financial settings
  await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/settings?documentId=financial_config`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(toFirestoreDoc({
      companyCash: 500000.0,
      totalProfitMargin: 25.5,
      secretTaxPin: '9988'
    }))
  });
}

async function runTests() {
  console.log('--- STARTING FIREBASE EMULATOR SECURITY RULES REAL VERIFICATION ---');
  await setupFixtures();

  let passed = 0;
  let total = 0;

  async function check(name, testFn) {
    total++;
    try {
      await testFn();
      console.log(`[PASS] ${name}`);
      passed++;
    } catch (err) {
      console.error(`[FAIL] ${name}: ${err.message}`);
    }
  }

  // ১. Active Device: অনুমোদিত Read/Write PASS
  await check('১. Active Device: অনুমোদিত Read/Write PASS', async () => {
    const devId = 'device_active_' + Date.now();
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices?documentId=${devId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        deviceId: devId,
        userId: SALESMAN_A_UID,
        deviceName: 'Samsung Galaxy A54',
        isRevoked: false
      }))
    });
    assert.strictEqual(res.status, 200, `Expected 200, got ${res.status}`);

    // Read back
    const readRes = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices/${devId}`, {
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}` }
    });
    assert.strictEqual(readRes.status, 200, `Expected 200 on read, got ${readRes.status}`);
  });

  // ২. Revoked Device: Read/Write DENIED (Re-activation attempt by normal user denied)
  await check('২. Revoked Device: Read/Write DENIED (Staff cannot un-revoke or re-activate revoked device)', async () => {
    // First, Owner marks device as revoked
    const devId = 'device_revoked_' + Date.now();
    await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices?documentId=${devId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        deviceId: devId,
        userId: SALESMAN_A_UID,
        deviceName: 'Stolen Phone',
        isRevoked: true
      }))
    });

    // Salesman attempts to update/un-revoke this device -> MUST BE 403 DENIED
    const updateRes = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices/${devId}?updateMask.fieldPaths=isRevoked`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        isRevoked: false
      }))
    });
    assert.strictEqual(updateRes.status, 403, `Expected 403, got ${updateRes.status}`);
  });

  // ৩. অন্য Shop-এর Data Access: DENIED
  await check('৩. অন্য Shop-এর Data Access: DENIED', async () => {
    // Salesman of Shop A attempts to read Shop B's products
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_B_ID}/products`, {
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}` }
    });
    assert.strictEqual(res.status, 403, `Expected 403, got ${res.status}`);
  });

  // ৪. অনুমোদিত Salesman Access: PASS
  await check('৪. অনুমোদিত Salesman Access: PASS', async () => {
    // Salesman creates an invoice/sale in Shop A
    const saleId = 'INV-TEST-SALESMAN-' + Date.now();
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/sales?documentId=${saleId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        invoiceNumber: saleId,
        customerId: 'cust_01',
        salesmanId: SALESMAN_A_UID,
        totalAmount: 1500.0,
        paidAmount: 1500.0,
        dueAmount: 0.0
      }))
    });
    assert.strictEqual(res.status, 200, `Expected 200, got ${res.status}`);
  });

  // ৫. Salesman-এর নিষিদ্ধ Financial Data Access: DENIED
  await check('৫. Salesman-এর নিষিদ্ধ Financial Data Access: DENIED', async () => {
    // Salesman A attempts to read Shop A settings (company cash, profit margins)
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/settings/financial_config`, {
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}` }
    });
    assert.strictEqual(res.status, 403, `Expected 403, got ${res.status}`);
  });

  // ৬. Shop Owner নিজের Shop Access: PASS
  await check('৬. Shop Owner নিজের Shop Access: PASS', async () => {
    // Owner of Shop A reads their own shop settings
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/settings/financial_config`, {
      headers: { 'Authorization': `Bearer ${OWNER_A_TOKEN}` }
    });
    assert.strictEqual(res.status, 200, `Expected 200, got ${res.status}`);
  });

  // ৭. Shop Owner অন্য Shop Access: DENIED
  await check('৭. Shop Owner অন্য Shop Access: DENIED', async () => {
    // Owner of Shop A attempts to read Shop B's settings
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_B_ID}/settings/financial_config`, {
      headers: { 'Authorization': `Bearer ${OWNER_A_TOKEN}` }
    });
    assert.strictEqual(res.status, 403, `Expected 403, got ${res.status}`);
  });

  // ৮. অনুমোদিত Super Admin Access: PASS
  await check('৮. অনুমোদিত Super Admin Access: PASS', async () => {
    // Super Admin accesses platform audit logs
    const res = await fetch(`${BASE_URL}/platform_audit_logs`, {
      headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}` }
    });
    assert.strictEqual(res.status, 200, `Expected 200, got ${res.status}`);
  });

  // ৯. সাধারণ ব্যবহারকারীর Super Admin Access: DENIED
  await check('৯. সাধারণ ব্যবহারকারীর Super Admin Access: DENIED', async () => {
    // Normal user attempts to read platform audit logs
    const res = await fetch(`${BASE_URL}/platform_audit_logs`, {
      headers: { 'Authorization': `Bearer ${NORMAL_USER_TOKEN}` }
    });
    assert.strictEqual(res.status, 403, `Expected 403, got ${res.status}`);
  });

  // ১০. Device ID পরিবর্তন বা জাল করার চেষ্টা: DENIED
  await check('১০. Device ID পরিবর্তন বা জাল করার চেষ্টা: DENIED', async () => {
    // Salesman attempts to create a device record with spoofed user ID (not matching their auth UID)
    const spoofedDevId = 'device_spoofed_999';
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices?documentId=${spoofedDevId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        deviceId: spoofedDevId,
        userId: 'different_user_uid_spoofed',
        deviceName: 'Hacked Device',
        isRevoked: false
      }))
    });
    assert.strictEqual(res.status, 403, `Expected 403 for spoofed device creation, got ${res.status}`);
  });

  // ১১. Revoked Device দিয়ে নতুন Sale বা Sync করার চেষ্টা: DENIED
  await check('১১. Revoked Device দিয়ে নতুন Sale বা Sync করার চেষ্টা: DENIED', async () => {
    // Ensure revoked device is recorded
    const revokedDevId = 'dev_blacklisted_777';
    await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/active_devices?documentId=${revokedDevId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SUPER_ADMIN_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        deviceId: revokedDevId,
        userId: SALESMAN_A_UID,
        isRevoked: true
      }))
    });

    // Attempt to create a sale tagging this revoked deviceId
    const saleId = 'INV-REVOKED-DEVICE-BLOCKED';
    const res = await fetch(`${BASE_URL}/businesses/${SHOP_A_ID}/sales?documentId=${saleId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${SALESMAN_A_TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(toFirestoreDoc({
        invoiceNumber: saleId,
        customerId: 'cust_01',
        salesmanId: SALESMAN_A_UID,
        totalAmount: 1200.0,
        paidAmount: 1200.0,
        dueAmount: 0.0,
        deviceId: revokedDevId
      }))
    });
    assert.strictEqual(res.status, 403, `Expected 403 for sale from revoked device, got ${res.status}`);
  });

  console.log(`\n--- EMULATOR TEST SUMMARY: ${passed}/${total} TESTS PASSED ---`);
  if (passed !== total) process.exit(1);
}

runTests();
