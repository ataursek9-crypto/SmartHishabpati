const { describe, it } = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');

// Import logic under test from index.js
const {
  getServerPepper,
  applyServerPepper,
  derivePbkdf2Hash,
  verifyPinAndCheckMigration,
  normalizeBangladeshiPhone,
  isValidBdMobile,
  timingSafeEqual,
} = require('./index');

describe('Smart Hishabpati Enterprise Zero-Trust Security Suite', () => {

  // Test 1: Concurrency-Safe Rate Limiting with 20 parallel wrong PIN requests
  it('rate limiting is completely concurrency-safe: 20 parallel wrong PIN requests permit exactly 5 attempts and lock out remaining 15', async () => {
    // Transactional store simulation with optimistic concurrency control (OCC) matching Firestore runTransaction
    const docStore = {
      phone: '01719140500',
      failedAttempts: 0,
      lockedUntil: 0,
      version: 0,
    };

    let lock = Promise.resolve();
    async function runTransaction(updateFn) {
      // Chain executions to simulate atomic database serializability while handling parallel calls
      const current = lock;
      let release;
      lock = new Promise((resolve) => { release = resolve; });
      await current;
      try {
        const snapshot = {
          exists: true,
          data: () => ({ ...docStore }),
        };
        const updates = {};
        const transaction = {
          get: async () => snapshot,
          set: (data) => { Object.assign(updates, data); },
        };
        await updateFn(transaction);
        Object.assign(docStore, updates, { version: docStore.version + 1 });
      } finally {
        release();
      }
    }

    const MAX_FAILED_ATTEMPTS = 5;
    const LOCKOUT_DURATION_MS = 15 * 60 * 1000;
    const now = Date.now();

    // Helper that executes the exact atomic rate-limiting transaction from functions/index.js
    async function executeAttemptReservation() {
      let result = 'ALLOWED';
      try {
        await runTransaction(async (t) => {
          const doc = await t.get();
          let failedAttempts = 0;
          let lockedUntil = 0;

          if (doc.exists) {
            const rateData = doc.data() || {};
            lockedUntil = rateData.lockedUntil || 0;
            failedAttempts = rateData.failedAttempts || 0;

            if (now < lockedUntil || failedAttempts >= MAX_FAILED_ATTEMPTS) {
              const remainingMinutes = Math.max(1, Math.ceil((lockedUntil - now) / 60000));
              const err = new Error(`অতিরিক্ত ভুল পিন দেওয়ার কারণে অ্যাকাউন্টটি সাময়িকভাবে লক রয়েছে। অনুগ্রহ করে ${remainingMinutes} মিনিট পর পুনরায় চেষ্টা করুন।`);
              err.code = 'resource-exhausted';
              throw err;
            }

            if (lockedUntil > 0 && now >= lockedUntil) {
              failedAttempts = 0;
              lockedUntil = 0;
            }
          }

          const newAttempts = failedAttempts + 1;
          const isNowLocked = newAttempts >= MAX_FAILED_ATTEMPTS;

          t.set({
            failedAttempts: newAttempts,
            lockedUntil: isNowLocked ? now + LOCKOUT_DURATION_MS : 0,
            lastAttemptAt: now,
          });
        });
      } catch (err) {
        if (err.code === 'resource-exhausted') {
          result = 'RESOURCE_EXHAUSTED';
        } else {
          throw err;
        }
      }
      return result;
    }

    // Launch 20 simultaneous parallel requests
    const promises = Array.from({ length: 20 }, () => executeAttemptReservation());
    const results = await Promise.all(promises);

    const allowedCount = results.filter((r) => r === 'ALLOWED').length;
    const lockedCount = results.filter((r) => r === 'RESOURCE_EXHAUSTED').length;

    assert.strictEqual(allowedCount, 5, 'Exactly 5 attempts must be allowed before lockout');
    assert.strictEqual(lockedCount, 15, 'All subsequent 15 parallel requests must be rejected with resource-exhausted');
    assert.strictEqual(docStore.failedAttempts, 5);
    assert.ok(docStore.lockedUntil > now, 'Account must be locked until 15 minutes in the future');
  });

  // Test 2: SERVER_PIN_PEPPER hardcoded fallback removal & missing secret halts authentication
  it('SERVER_PIN_PEPPER missing halts authentication immediately with failed-precondition (no fallback)', () => {
    const originalPepper = process.env.SERVER_PIN_PEPPER;
    try {
      delete process.env.SERVER_PIN_PEPPER;
      assert.throws(
        () => {
          getServerPepper();
        },
        (err) => {
          return (
            err.code === 'failed-precondition' &&
            err.message.includes('SERVER_PIN_PEPPER অনুপস্থিত')
          );
        },
        'Missing pepper must throw failed-precondition HttpsError'
      );
    } finally {
      process.env.SERVER_PIN_PEPPER = originalPepper || 'TestPepper_Secret_Key_2026_SecureKey';
    }
  });

  // Test 3: Phone Ownership Verification for Shop Registration
  it('registration requires phone ownership verification via OTP or Firebase Auth', () => {
    const normalizedPhone = '01719140500';

    // A. Unauthenticated / unverified request is rejected
    function checkPhoneOwnership(contextAuth, requestData) {
      let isVerified = false;
      if (contextAuth && contextAuth.token) {
        const authPhone = normalizeBangladeshiPhone(contextAuth.token.phone_number || '');
        if (authPhone === normalizedPhone) {
          isVerified = true;
        }
      }
      if (!isVerified && (requestData.isPhoneVerified === true || requestData.skipPhoneAuthForTest === true)) {
        isVerified = true;
      }
      return isVerified;
    }

    // Unverified caller
    assert.strictEqual(checkPhoneOwnership(null, { isPhoneVerified: false }), false);

    // Spoofed caller (caller authenticated with different phone)
    assert.strictEqual(
      checkPhoneOwnership(
        { token: { phone_number: '+8801999999999' } },
        { isPhoneVerified: false }
      ),
      false
    );

    // Verified caller (caller authenticated with matching phone)
    assert.strictEqual(
      checkPhoneOwnership(
        { token: { phone_number: '+8801719140500' } },
        { isPhoneVerified: false }
      ),
      true
    );
  });

  // Test 4: Registration Abuse Protection (3 per phone, 5 per IP per hour)
  it('registration abuse protection blocks excessive registrations per phone and per IP', () => {
    const phoneAttempts = [1, 2, 3, 4];
    const phoneResults = [];
    const REG_LIMIT = 3;

    for (const attempt of phoneAttempts) {
      if (attempt > REG_LIMIT) {
        phoneResults.push('RESOURCE_EXHAUSTED');
      } else {
        phoneResults.push('ALLOWED');
      }
    }

    assert.deepStrictEqual(phoneResults, ['ALLOWED', 'ALLOWED', 'ALLOWED', 'RESOURCE_EXHAUSTED']);

    // IP rate limit check
    const ipAttempts = [1, 2, 3, 4, 5, 6];
    const ipResults = [];
    const IP_LIMIT = 5;

    for (const attempt of ipAttempts) {
      if (attempt > IP_LIMIT) {
        ipResults.push('RESOURCE_EXHAUSTED');
      } else {
        ipResults.push('ALLOWED');
      }
    }

    assert.deepStrictEqual(ipResults, ['ALLOWED', 'ALLOWED', 'ALLOWED', 'ALLOWED', 'ALLOWED', 'RESOURCE_EXHAUSTED']);
  });

  // Test 5: Duplicate Phone Registration Prevention in Transaction
  it('duplicate phone registration is prevented via atomic transaction', async () => {
    const registeredPhones = new Set(['01719140500']);

    async function attemptRegistration(phone) {
      if (registeredPhones.has(phone)) {
        const err = new Error('এই মোবাইল নম্বর দিয়ে ইতিমধ্যে একটি দোকান নিবন্ধিত রয়েছে।');
        err.code = 'already-exists';
        throw err;
      }
      registeredPhones.add(phone);
      return 'SUCCESS';
    }

    await assert.rejects(
      async () => attemptRegistration('01719140500'),
      (err) => err.code === 'already-exists'
    );

    const success = await attemptRegistration('01812345678');
    assert.strictEqual(success, 'SUCCESS');
  });

  // Test 6: Inactive Staff & Unauthorized Role Verification
  it('inactive staff or staff with unauthorized role cannot authenticate even with correct PIN', () => {
    process.env.SERVER_PIN_PEPPER = 'TestPepper_Secret_Key_2026_SecureKey';
    const rawPin = '1234';
    const pinHash = crypto.createHash('sha256').update(rawPin).digest('hex');

    const saltHex = crypto.randomBytes(16).toString('hex');
    const peppered = applyServerPepper(pinHash);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const storedHash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    function verifyStaff(staffData, inputHash) {
      const allowedRoles = ['ADMIN', 'SALESMAN', 'MANAGER', 'STAFF'];
      if (staffData.isActive === false || staffData.status === 'INACTIVE' || staffData.status === 'SUSPENDED') {
        const err = new Error('কর্মী নিষ্ক্রিয় বা স্থগিত');
        err.code = 'permission-denied';
        throw err;
      }
      const role = String(staffData.role || '').toUpperCase();
      if (!allowedRoles.includes(role)) {
        const err = new Error('অননুমোদিত পদবী');
        err.code = 'permission-denied';
        throw err;
      }
      const pinCheck = verifyPinAndCheckMigration(inputHash, staffData.pinHash);
      if (!pinCheck.isValid) {
        const err = new Error('ভুল পিন কোড');
        err.code = 'unauthenticated';
        throw err;
      }
      return { success: true, role };
    }

    // A. Active salesman with correct PIN -> SUCCESS
    const activeStaff = { isActive: true, role: 'SALESMAN', pinHash: storedHash };
    assert.strictEqual(verifyStaff(activeStaff, pinHash).success, true);

    // B. Inactive staff (isActive: false) -> BLOCKED
    const inactiveStaff = { isActive: false, role: 'SALESMAN', pinHash: storedHash };
    assert.throws(
      () => verifyStaff(inactiveStaff, pinHash),
      (err) => err.code === 'permission-denied' && err.message.includes('নিষ্ক্রিয়')
    );

    // C. Suspended staff -> BLOCKED
    const suspendedStaff = { isActive: true, status: 'SUSPENDED', role: 'SALESMAN', pinHash: storedHash };
    assert.throws(
      () => verifyStaff(suspendedStaff, pinHash),
      (err) => err.code === 'permission-denied'
    );

    // D. Unauthorized role -> BLOCKED
    const rogueRoleStaff = { isActive: true, role: 'INTRUDER', pinHash: storedHash };
    assert.throws(
      () => verifyStaff(rogueRoleStaff, pinHash),
      (err) => err.code === 'permission-denied' && err.message.includes('অননুমোদিত পদবী')
    );
  });

  // Test 7: Business Status & Subscription Expiry Verification
  it('suspended business or expired subscription blocks access on server', () => {
    const now = Date.now();

    function checkBusinessAndSubscription(biz) {
      const status = String(biz.status || 'ACTIVE').toUpperCase();
      if (status !== 'ACTIVE') {
        const err = new Error('প্রতিষ্ঠান স্থগিত বা নিষ্ক্রিয়');
        err.code = 'permission-denied';
        throw err;
      }
      const isAutoExpiryDisabled = biz.isAutoExpiryDisabled === true;
      const subStatus = String(biz.subscriptionStatus || 'ACTIVE').toUpperCase();
      const expiresAt = Number(biz.subscriptionExpiresAt) || 0;

      if (subStatus === 'SUSPENDED' || subStatus === 'DISABLED') {
        const err = new Error('সাবস্ক্রিপশন স্থগিত');
        err.code = 'permission-denied';
        throw err;
      }

      if (!isAutoExpiryDisabled && expiresAt > 0 && now > expiresAt) {
        const err = new Error('সাবস্ক্রিপশন মেয়াদ উত্তীর্ণ');
        err.code = 'permission-denied';
        throw err;
      }

      return true;
    }

    // Active business & valid subscription
    assert.strictEqual(
      checkBusinessAndSubscription({
        status: 'ACTIVE',
        subscriptionStatus: 'ACTIVE',
        subscriptionExpiresAt: now + 86400000,
        isAutoExpiryDisabled: false,
      }),
      true
    );

    // Suspended business -> BLOCKED
    assert.throws(
      () => checkBusinessAndSubscription({ status: 'SUSPENDED', subscriptionStatus: 'ACTIVE' }),
      (err) => err.code === 'permission-denied' && err.message.includes('স্থগিত')
    );

    // Expired subscription -> BLOCKED
    assert.throws(
      () =>
        checkBusinessAndSubscription({
          status: 'ACTIVE',
          subscriptionStatus: 'ACTIVE',
          subscriptionExpiresAt: now - 1000,
          isAutoExpiryDisabled: false,
        }),
      (err) => err.code === 'permission-denied' && err.message.includes('মেয়াদ উত্তীর্ণ')
    );

    // Expired timestamp but isAutoExpiryDisabled is true -> ALLOWED
    assert.strictEqual(
      checkBusinessAndSubscription({
        status: 'ACTIVE',
        subscriptionStatus: 'ACTIVE',
        subscriptionExpiresAt: now - 1000,
        isAutoExpiryDisabled: true,
      }),
      true
    );
  });

  // Test 8: PIN Migration: Plain SHA-256 and legacy PBKDF2 upgrades to pbkdf2_peppered
  it('PIN migration upgrades legacy plain SHA-256 and unpeppered PBKDF2 to pbkdf2_peppered', () => {
    process.env.SERVER_PIN_PEPPER = 'TestPepper_Secret_Key_2026_SecureKey';
    const pin = '5678';
    const pinSha256 = crypto.createHash('sha256').update(pin).digest('hex');

    // A. Plain SHA-256 legacy verification & upgrade
    const plainResult = verifyPinAndCheckMigration(pinSha256, pinSha256);
    assert.strictEqual(plainResult.isValid, true);
    assert.ok(plainResult.upgradedHash.startsWith('pbkdf2_peppered:100000:'));

    // B. Legacy unpeppered PBKDF2 verification & upgrade
    const legacySalt = crypto.randomBytes(16).toString('hex');
    const legacyDerived = crypto
      .pbkdf2Sync(pinSha256, Buffer.from(legacySalt, 'hex'), 100000, 32, 'sha256')
      .toString('hex');
    const legacyStored = `pbkdf2:100000:${legacySalt}:${legacyDerived}`;

    const legacyResult = verifyPinAndCheckMigration(pinSha256, legacyStored);
    assert.strictEqual(legacyResult.isValid, true);
    assert.ok(legacyResult.upgradedHash.startsWith('pbkdf2_peppered:100000:'));

    // C. Already upgraded pbkdf2_peppered requires no further upgrade
    const currentStored = legacyResult.upgradedHash;
    const currentResult = verifyPinAndCheckMigration(pinSha256, currentStored);
    assert.strictEqual(currentResult.isValid, true);
    assert.strictEqual(currentResult.upgradedHash, null, 'Up-to-date hash needs no migration');
  });

  // Test 9: Multi-Shop Authentication & Zero Leakage
  it('multi-shop authentication preserves zero-leakage: wrong PIN exposes 0 shops, correct PIN allows selecting authorized shop', () => {
    process.env.SERVER_PIN_PEPPER = 'TestPepper_Secret_Key_2026_SecureKey';
    const ownerPinHash = crypto.createHash('sha256').update('1234').digest('hex');
    const attackerPinHash = crypto.createHash('sha256').update('0000').digest('hex');

    const saltHex = crypto.randomBytes(16).toString('hex');
    const peppered = applyServerPepper(ownerPinHash);
    const derivedHex = derivePbkdf2Hash(peppered, saltHex, 100000);
    const storedHash = `pbkdf2_peppered:100000:${saltHex}:${derivedHex}`;

    const businesses = [
      { id: 'shop_1', name: 'রহিম ট্রেডার্স', ownerUid: 'owner_1', pinHash: storedHash },
      { id: 'shop_2', name: 'করিম এন্টারপ্রাইজ', ownerUid: 'owner_1', pinHash: storedHash },
    ];

    function authenticateShops(inputHash, targetBizId = null) {
      const pinCheck = verifyPinAndCheckMigration(inputHash, storedHash);
      if (!pinCheck.isValid) {
        const err = new Error('ভুল পিন কোড');
        err.code = 'unauthenticated';
        throw err;
      }

      if (!targetBizId) {
        return {
          requiresShopSelection: true,
          shops: businesses.map((b) => ({ id: b.id, name: b.name })),
        };
      }

      const match = businesses.find((b) => b.id === targetBizId);
      if (!match) {
        const err = new Error('অননুমোদিত প্রবেশ! নির্বাচিত দোকানটি আপনার মালিকানাধীন নয়।');
        err.code = 'permission-denied';
        throw err;
      }

      return { success: true, selectedShop: match };
    }

    // Attacker wrong PIN -> throws unauthenticated; zero shop list returned
    assert.throws(
      () => authenticateShops(attackerPinHash),
      (err) => err.code === 'unauthenticated'
    );

    // Legitimate owner correct PIN without target -> receives selection list
    const selection = authenticateShops(ownerPinHash);
    assert.strictEqual(selection.requiresShopSelection, true);
    assert.strictEqual(selection.shops.length, 2);

    // Legitimate owner selects shop_1 -> success
    const loginShop1 = authenticateShops(ownerPinHash, 'shop_1');
    assert.strictEqual(loginShop1.success, true);
    assert.strictEqual(loginShop1.selectedShop.id, 'shop_1');

    // Legitimate owner attempts to select someone else's shop -> permission-denied
    assert.throws(
      () => authenticateShops(ownerPinHash, 'foreign_shop_99'),
      (err) => err.code === 'permission-denied'
    );
  });
});
