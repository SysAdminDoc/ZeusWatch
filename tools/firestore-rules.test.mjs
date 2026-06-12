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
  setDoc,
  updateDoc,
} from "firebase/firestore";

const PROJECT_ID = "zeuswatch-rules-test";
const COLLECTION = "community_reports";
const DEVICE_ID = "a".repeat(64);
const OTHER_DEVICE_ID = "b".repeat(64);

const rules = await readFile("firestore.rules", "utf8");

const testEnv = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: { rules },
});

const tests = [];

function test(name, fn) {
  tests.push({ name, fn });
}

function report(overrides = {}) {
  return {
    latitude: 39.7392,
    longitude: -104.9903,
    condition: "RAIN",
    deviceId: DEVICE_ID,
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

async function seedReport(id, data = report()) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), COLLECTION, id), data);
  });
}

test("allows anonymous reads and valid creates", async () => {
  const db = dbFor();

  await assertSucceeds(setDoc(doc(db, COLLECTION, "valid"), report()));
  await assertSucceeds(getDocs(collection(db, COLLECTION)));
});

test("rejects malformed report payloads", async () => {
  const db = dbFor();

  await assertFails(setDoc(doc(db, COLLECTION, "missing-longitude"), {
    latitude: 39.7392,
    condition: "RAIN",
    deviceId: DEVICE_ID,
    timestamp: Date.now(),
  }));
  await assertFails(setDoc(doc(db, COLLECTION, "extra-field"), report({ owner: "client" })));
  await assertFails(setDoc(doc(db, COLLECTION, "bad-latitude"), report({ latitude: "39.7" })));
});

test("rejects stale and future report timestamps", async () => {
  const db = dbFor();

  await assertFails(
    setDoc(doc(db, COLLECTION, "stale"), report({ timestamp: Date.now() - 11 * 60 * 1000 })),
  );
  await assertFails(
    setDoc(doc(db, COLLECTION, "future"), report({ timestamp: Date.now() + 3 * 60 * 1000 })),
  );
});

test("rejects invalid report conditions", async () => {
  const db = dbFor();

  await assertFails(setDoc(doc(db, COLLECTION, "invalid-condition"), report({ condition: "METEOR" })));
});

test("rejects invalid anonymous device ids", async () => {
  const db = dbFor();

  await assertFails(setDoc(doc(db, COLLECTION, "short-device-id"), report({ deviceId: "abc123" })));
  await assertFails(
    setDoc(doc(db, COLLECTION, "non-hex-device-id"), report({ deviceId: "g".repeat(64) })),
  );
});

test("denies updates and deletes under append-only anonymous model", async () => {
  await seedReport("owned", report({ deviceId: DEVICE_ID }));
  await seedReport("other", report({ deviceId: OTHER_DEVICE_ID }));

  const ownerDb = dbFor("owner");
  const otherDb = dbFor("other");

  await assertFails(updateDoc(doc(ownerDb, COLLECTION, "owned"), { note: "Edited" }));
  await assertFails(deleteDoc(doc(ownerDb, COLLECTION, "owned")));
  await assertFails(deleteDoc(doc(otherDb, COLLECTION, "owned")));
  await assertFails(deleteDoc(doc(ownerDb, COLLECTION, "other")));
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
