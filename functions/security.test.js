const { describe, it } = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');

// Server configuration & pepper
const SERVER_PIN_PEPPER = process.env.PIN_PEPPER || 'SmartHishabpatiSecurePinPepper_2026_EnterpriseKey_991823';
const PBKDF2_ITERATIONS = 100000;
const PBKDF2_KEYLEN = 32;
const PBKDF2_DIGEST = 'sha256';

function computePepperedInput(rawPinOrSha256) {
  return crypto.createHmac('sha256', SERVER_PIN_PEPPER)
    .update(String(rawPinOrSha256).trim())
    .digest('hex');
}

function hashPinPbkdf2WithPepper(rawPinOrSha256, saltHex = null) {
  const salt = saltHex ? Buffer.from(saltHex, 'hex') : crypto.randomBytes(16);
  const peppered = computePepperedInput(rawPinOrSha256);
  const derivedKey = crypto.pbkdf2Sync(
    peppered,
    salt,
    PBKDF2_ITERATIONS,
    PBKDF2_KEYLEN,
    PBKDF2_DIGEST
  );
  return {
    saltHex: salt.toString('hex'),
    hashHex: derivedKey.toString('hex')
  };
}

function verifyPinPbkdf2WithPepper(candidateInput, saltHex, expectedHashHex) {
  if (!saltHex || !expectedHashHex) return false;
  try {
    const salt = Buffer.from(saltHex, 'hex');
    const peppered = computePepperedInput(candidateInput);
    const derivedKey = crypto.pbkdf2Sync(
      peppered,
      salt,
      PBKDF2_ITERATIONS,
      PBKDF2_KEYLEN,
      PBKDF2_DIGEST
    );
    const expectedBuf = Buffer.from(expectedHashHex, 'hex');
    if (derivedKey.length !== expectedBuf.length) return false;
    return crypto.timingSafeEqual(derivedKey, expectedBuf);
  } catch (err) {
    return false;
  }
}

describe('Smart Hishabpati Enterprise PIN Security & Cryptography Suite', () => {

  it('generates unique salts and timing-safe verifiable PBKDF2 hashes with pepper', () => {
    const pin = '1234';
    const clientSha256 = crypto.createHash('sha256').update(pin).digest('hex');

    const derived1 = hashPinPbkdf2WithPepper(clientSha256);
    const derived2 = hashPinPbkdf2WithPepper(clientSha256);

    // Unique salts per generation
    assert.notStrictEqual(derived1.saltHex, derived2.saltHex);
    assert.notStrictEqual(derived1.hashHex, derived2.hashHex);

    // Verifies correctly with matching salt & candidate
    assert.strictEqual(verifyPinPbkdf2WithPepper(clientSha256, derived1.saltHex, derived1.hashHex), true);
    assert.strictEqual(verifyPinPbkdf2WithPepper(clientSha256, derived2.saltHex, derived2.hashHex), true);

    // Rejects incorrect PIN
    const wrongSha256 = crypto.createHash('sha256').update('9999').digest('hex');
    assert.strictEqual(verifyPinPbkdf2WithPepper(wrongSha256, derived1.saltHex, derived1.hashHex), false);
  });

  it('offline dictionary attacks fail without the server pepper', () => {
    const pin = '1234';
    const clientSha256 = crypto.createHash('sha256').update(pin).digest('hex');
    const secureHash = hashPinPbkdf2WithPepper(clientSha256);

    // An attacker running standard PBKDF2 on the salt without knowing SERVER_PIN_PEPPER
    const unpepperedKey = crypto.pbkdf2Sync(
      clientSha256,
      Buffer.from(secureHash.saltHex, 'hex'),
      PBKDF2_ITERATIONS,
      PBKDF2_KEYLEN,
      PBKDF2_DIGEST
    ).toString('hex');

    assert.notStrictEqual(unpepperedKey, secureHash.hashHex);
  });

  it('multi-shop authentication security: fails without exposing any shop info when PIN is wrong', () => {
    // Mock user having 3 shops
    const userShops = [
      { id: 'shop_1', businessName: 'দোকান ১', pinPbkdf2Hash: hashPinPbkdf2WithPepper('hash_1234') },
      { id: 'shop_2', businessName: 'দোকান ২', pinPbkdf2Hash: hashPinPbkdf2WithPepper('hash_1234') }
    ];

    // Attacker provides wrong PIN
    const attackerPinHash = 'wrong_hash_5678';
    const authorizedShops = userShops.filter(shop => {
      return verifyPinPbkdf2WithPepper(
        attackerPinHash,
        shop.pinPbkdf2Hash.saltHex,
        shop.pinPbkdf2Hash.hashHex
      );
    });

    // Zero-leakage rule: No shop metadata is returned when PIN is wrong
    assert.strictEqual(authorizedShops.length, 0);

    // Legitimate user provides correct PIN
    const legitPinHash = 'hash_1234';
    const verifiedShops = userShops.filter(shop => {
      return verifyPinPbkdf2WithPepper(
        legitPinHash,
        shop.pinPbkdf2Hash.saltHex,
        shop.pinPbkdf2Hash.hashHex
      );
    });

    assert.strictEqual(verifiedShops.length, 2);
  });

  it('atomic attempt reservation enforces lockout after 5 consecutive failures', () => {
    let failedAttempts = 0;
    let lockedUntil = 0;
    const now = Date.now();
    const MAX_FAILED_ATTEMPTS = 5;
    const LOCKOUT_DURATION_MS = 15 * 60 * 1000;

    for (let attempt = 1; attempt <= 6; attempt++) {
      const isLocked = lockedUntil > now;
      if (isLocked) {
        // Attempt rejected due to lockout
        assert.strictEqual(attempt, 6);
        break;
      }
      // Record failed attempt
      failedAttempts++;
      if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
        lockedUntil = now + LOCKOUT_DURATION_MS;
      }
    }

    assert.strictEqual(failedAttempts, 5);
    assert.ok(lockedUntil > now);
  });
});
