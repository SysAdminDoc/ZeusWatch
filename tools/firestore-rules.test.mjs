import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  limit,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from "firebase/firestore";

const PROJECT_ID = "zeuswatch-rules-test";
const COLLECTION = "community_reports";
const THROTTLES = "report_throttles";
const UID = "anon-user-1";
const OTHER_UID = "anon-user-2";
const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

const rules = await readFile("firestore.rules", "utf8");
const indexes = JSON.parse(await readFile("firestore.indexes.json", "utf8"));

const testEnv = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: { rules },
});

const tests = [];

function test(name, fn) {
  tests.push({ name, fn });
}

// Reports are stored without an owner id (readers must not be able to correlate
// one account's reports); the rate-limit binding is the separate throttle ledger.
function report(overrides = {}) {
  const timestamp = overrides.timestamp ?? Date.now();
  return {
    latitude: 39.74,
    longitude: -104.99,
    geohash: "9xj64",
    condition: "RAIN",
    timestamp,
    expiresAt: new Date(timestamp + TWO_HOURS_MS),
    note: "Light rain starting",
    ...overrides,
  };
}

// A bounded nearby-style query, matching the app's geohash+timestamp+limit(50) read.
function boundedQuery(db) {
  return query(collection(db, COLLECTION), limit(50));
}

function dbFor(uid = null) {
  return uid
    ? testEnv.authenticatedContext(uid).firestore()
    : testEnv.unauthenticatedContext().firestore();
}

// Submit a report the way the app does: a single atomic batch that creates the
// report doc and bumps the per-account throttle ledger to the commit time.
function submit(db, uid, reportId, overrides = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, COLLECTION, reportId), report(overrides));
  batch.set(doc(db, THROTTLES, uid), { lastReportAt: serverTimestamp() });
  return batch.commit();
}

async function seedReport(id, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), COLLECTION, id), data);
  });
}

test("requires an authenticated account for reads and creates", async () => {
  const anon = dbFor();
  await assertFails(getDocs(boundedQuery(anon)));
  await assertFails(submit(anon, UID, "anon-create"));

  const db = dbFor(UID);
  await assertSucceeds(submit(db, UID, "valid"));
  await assertSucceeds(getDocs(boundedQuery(db)));
});

test("declares a non-indexed TTL policy for report expiry", async () => {
  assert.deepEqual(
    indexes.fieldOverrides.find(
      (field) => field.collectionGroup === COLLECTION && field.fieldPath === "expiresAt",
    ),
    {
      collectionGroup: COLLECTION,
      fieldPath: "expiresAt",
      ttl: true,
      indexes: [],
    },
  );
});

test("caps collection query page size and denies unbounded reads", async () => {
  const db = dbFor(UID);
  // A bounded page (the app queries with limit(50)) is allowed.
  await assertSucceeds(getDocs(query(collection(db, COLLECTION), limit(50))));
  // An oversized page is rejected server-side.
  await assertFails(getDocs(query(collection(db, COLLECTION), limit(51))));
  // An unbounded collection read (the whole-firehose case) is rejected.
  await assertFails(getDocs(collection(db, COLLECTION)));
});

test("enforces a per-account server-side write-rate limit", async () => {
  const db = dbFor(UID);
  await assertSucceeds(submit(db, UID, "first"));
  // Second report from the same account within the window is rejected even
  // though the client-side limiter is bypassed (separate raw SDK call).
  await assertFails(submit(db, UID, "second"));

  // A different account is unaffected by the first account's throttle.
  const other = dbFor(OTHER_UID);
  await assertSucceeds(submit(other, OTHER_UID, "other-first"));
});

test("rejects a report create without the matching throttle bump", async () => {
  const db = dbFor(UID);
  // Report alone, no throttle write -> getAfter() rate-limit check fails.
  await assertFails(setDoc(doc(db, COLLECTION, "no-throttle"), report()));

  // Throttle doc that backdates lastReportAt instead of using the commit time.
  const batch = writeBatch(db);
  batch.set(doc(db, COLLECTION, "backdated"), report());
  batch.set(doc(db, THROTTLES, UID), { lastReportAt: Date.now() - 60_000 });
  await assertFails(batch.commit());
});

test("rejects reports carrying an owner id field", async () => {
  // Reports must not store an owner id (correlation vector); hasOnly() rejects it.
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "with-owner", { ownerUid: UID }));
});

test("rejects malformed report payloads", async () => {
  const db = dbFor(UID);

  const missingLon = writeBatch(db);
  missingLon.set(doc(db, COLLECTION, "missing-longitude"), {
    latitude: 39.7392,
    condition: "RAIN",
    timestamp: Date.now(),
  });
  missingLon.set(doc(db, THROTTLES, UID), { lastReportAt: serverTimestamp() });
  await assertFails(missingLon.commit());

  await assertFails(submit(db, UID, "extra-field", { owner: "client" }));
  await assertFails(submit(db, UID, "bad-latitude", { latitude: "39.7" }));
});

test("rejects exact coordinates that bypass area rounding", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "exact-location", {
    latitude: 39.7392,
    longitude: -104.9903,
  }));
  await assertSucceeds(submit(db, UID, "coarsened-location", {
    latitude: 39.74,
    longitude: -104.99,
  }));
});

test("requires a valid geohash on new report creates", async () => {
  const db = dbFor(UID);

  const missingGeohash = report();
  delete missingGeohash.geohash;
  const missingBatch = writeBatch(db);
  missingBatch.set(doc(db, COLLECTION, "missing-geohash"), missingGeohash);
  missingBatch.set(doc(db, THROTTLES, UID), { lastReportAt: serverTimestamp() });
  await assertFails(missingBatch.commit());

  await assertFails(submit(db, UID, "short-geohash", { geohash: "9x" }));
  await assertFails(submit(db, UID, "uppercase-geohash", { geohash: "9XJ64" }));
  await assertFails(submit(db, UID, "symbol-geohash", { geohash: "9xj6!" }));
});

test("allows reading recent legacy reports that predate geohash writes", async () => {
  const legacy = report();
  delete legacy.geohash;
  await seedReport("legacy-without-geohash", legacy);

  await assertSucceeds(getDocs(boundedQuery(dbFor(UID))));
});

test("rejects stale and future report timestamps", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "stale", { timestamp: Date.now() - 11 * 60 * 1000 }));
  await assertFails(submit(db, UID, "future", { timestamp: Date.now() + 3 * 60 * 1000 }));
});

test("requires a timestamp expiry approximately two hours after creation", async () => {
  const db = dbFor(UID);
  const timestamp = Date.now();

  const missingExpiry = report({ timestamp });
  delete missingExpiry.expiresAt;
  const missingBatch = writeBatch(db);
  missingBatch.set(doc(db, COLLECTION, "missing-expiry"), missingExpiry);
  missingBatch.set(doc(db, THROTTLES, UID), { lastReportAt: serverTimestamp() });
  await assertFails(missingBatch.commit());

  await assertFails(submit(db, UID, "numeric-expiry", {
    timestamp,
    expiresAt: timestamp + TWO_HOURS_MS,
  }));
  await assertFails(submit(db, UID, "short-retention", {
    timestamp,
    expiresAt: new Date(timestamp + 90 * 60 * 1000),
  }));
  await assertFails(submit(db, UID, "long-retention", {
    timestamp,
    expiresAt: new Date(timestamp + 3 * 60 * 60 * 1000),
  }));
});

test("rejects invalid report conditions", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "invalid-condition", { condition: "METEOR" }));
});

test("denies updates and deletes under append-only anonymous model", async () => {
  await seedReport("owned", report());
  await seedReport("other", report());

  const ownerDb = dbFor(UID);
  const otherDb = dbFor(OTHER_UID);

  await assertFails(updateDoc(doc(ownerDb, COLLECTION, "owned"), { note: "Edited" }));
  await assertFails(deleteDoc(doc(ownerDb, COLLECTION, "owned")));
  await assertFails(deleteDoc(doc(otherDb, COLLECTION, "owned")));
  await assertFails(deleteDoc(doc(ownerDb, COLLECTION, "other")));
});

test("isolates the throttle ledger to its owning account", async () => {
  const db = dbFor(UID);
  // Cannot write another account's throttle doc.
  await assertFails(
    setDoc(doc(db, THROTTLES, OTHER_UID), { lastReportAt: serverTimestamp() }),
  );
  // Cannot read another account's throttle doc.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), THROTTLES, OTHER_UID), { lastReportAt: 1 });
  });
  await assertFails(getDocs(collection(db, THROTTLES)));
});

try {
  for (const { name, fn } of tests) {
    await testEnv.clearFirestore();
    await fn();
    console.log(`ok - ${name}`);
  }
  console.log(`${tests.length} Firestore rules tests passed`);
} finally {
  await testEnv.cleanup();
}

assert.equal(process.exitCode ?? 0, 0);
