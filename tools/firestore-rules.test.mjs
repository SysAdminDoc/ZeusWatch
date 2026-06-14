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

const rules = await readFile("firestore.rules", "utf8");

const testEnv = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: { rules },
});

const tests = [];

function test(name, fn) {
  tests.push({ name, fn });
}

function report(uid, overrides = {}) {
  return {
    latitude: 39.7392,
    longitude: -104.9903,
    condition: "RAIN",
    ownerUid: uid,
    timestamp: Date.now(),
    note: "Light rain starting",
    ...overrides,
  };
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
  batch.set(doc(db, COLLECTION, reportId), report(uid, overrides));
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
  await assertFails(getDocs(collection(anon, COLLECTION)));
  await assertFails(submit(anon, UID, "anon-create"));

  const db = dbFor(UID);
  await assertSucceeds(submit(db, UID, "valid"));
  await assertSucceeds(getDocs(collection(db, COLLECTION)));
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
  await assertFails(setDoc(doc(db, COLLECTION, "no-throttle"), report(UID)));

  // Throttle doc that backdates lastReportAt instead of using the commit time.
  const batch = writeBatch(db);
  batch.set(doc(db, COLLECTION, "backdated"), report(UID));
  batch.set(doc(db, THROTTLES, UID), { lastReportAt: Date.now() - 60_000 });
  await assertFails(batch.commit());
});

test("rejects ownerUid spoofing", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "spoofed", { ownerUid: OTHER_UID }));
});

test("rejects malformed report payloads", async () => {
  const db = dbFor(UID);

  const missingLon = writeBatch(db);
  missingLon.set(doc(db, COLLECTION, "missing-longitude"), {
    latitude: 39.7392,
    condition: "RAIN",
    ownerUid: UID,
    timestamp: Date.now(),
  });
  missingLon.set(doc(db, THROTTLES, UID), { lastReportAt: serverTimestamp() });
  await assertFails(missingLon.commit());

  await assertFails(submit(db, UID, "extra-field", { owner: "client" }));
  await assertFails(submit(db, UID, "bad-latitude", { latitude: "39.7" }));
});

test("rejects stale and future report timestamps", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "stale", { timestamp: Date.now() - 11 * 60 * 1000 }));
  await assertFails(submit(db, UID, "future", { timestamp: Date.now() + 3 * 60 * 1000 }));
});

test("rejects invalid report conditions", async () => {
  const db = dbFor(UID);
  await assertFails(submit(db, UID, "invalid-condition", { condition: "METEOR" }));
});

test("denies updates and deletes under append-only anonymous model", async () => {
  await seedReport("owned", report(UID));
  await seedReport("other", report(OTHER_UID));

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
