const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_store_001";
const BOB_UID = "bob_store_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
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

function validItemPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: 'Jeans Button — "DENIM CO."',
    extractedText: "DENIM CO.",
    designDetails: "Matte black circular shank button, single rim",
    category: "Hardware",
    quantity: 24,
    threshold: 5,
    fpHash: "0101010101010101",
    fpAvgRgb: [32, 34, 38],
    fpRadial: [28, 36, 44],
    fpEdgeDensity: 18,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validHistoryPayload(uid, itemId, overrides = {}) {
  return {
    userId: uid,
    itemId,
    type: "IN",
    itemName: 'Jeans Button — "DENIM CO." [DENIM CO.]',
    quantity: 24,
    resultingStock: 24,
    offlineQueued: false,
    createdAt: new Date(Date.now() - 1000),
    ...overrides,
  };
}

test("Unauthenticated user: denied read/write on inventory and history", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("inventory").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("inventory").doc("itm_1").set(validItemPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("history").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("history").doc("hist_1").set(validHistoryPayload(ALICE_UID, "itm_1"))
  );
});

test("Authenticated owner: valid Kotlin inventory create, list, quick adjust, edit & delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invCol = aliceDb.collection("users").doc(ALICE_UID).collection("inventory");

  // Create
  await assertSucceeds(invCol.doc("itm_1").set(validItemPayload(ALICE_UID)));

  // List & Get
  await assertSucceeds(invCol.get());
  await assertSucceeds(invCol.doc("itm_1").get());

  // Action 1: Quick +/- adjust (partial update)
  await assertSucceeds(
    invCol.doc("itm_1").update({
      quantity: 25,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 2: Full variant edit (preserving createdAt & userId)
  await assertSucceeds(
    invCol.doc("itm_1").update({
      name: 'Jeans Button — "ESTD 1966"',
      extractedText: "ESTD 1966",
      designDetails: "Glossy black double rim",
      category: "Denim Hardware",
      quantity: 30,
      threshold: 8,
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Delete
  await assertSucceeds(invCol.doc("itm_1").delete());
});

test("Security bounds: rejects shadow/ghost fields, @DocumentId id field, immutable mutations, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invCol = aliceDb.collection("users").doc(ALICE_UID).collection("inventory");

  // Reject ghost field
  await assertFails(
    invCol.doc("itm_ghost").set(validItemPayload(ALICE_UID, { isVerified: true }))
  );

  // Reject @DocumentId 'id' inside payload body
  await assertFails(
    invCol.doc("itm_with_id").set(validItemPayload(ALICE_UID, { id: "itm_with_id" }))
  );

  // Reject future timestamp
  await assertFails(
    invCol.doc("itm_future").set(validItemPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject negative quantity
  await assertFails(
    invCol.doc("itm_neg").set(validItemPayload(ALICE_UID, { quantity: -1 }))
  );

  // Reject invalid document ID characters
  await assertFails(
    invCol.doc("bad id!").set(validItemPayload(ALICE_UID))
  );

  // Seed valid item and test immutable field tampering
  await assertSucceeds(invCol.doc("itm_valid").set(validItemPayload(ALICE_UID)));

  // Reject changing createdAt
  await assertFails(
    invCol.doc("itm_valid").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject changing userId
  await assertFails(
    invCol.doc("itm_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's inventory or history", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).collection("inventory").doc("itm_alice").set(validItemPayload(ALICE_UID));
    await adminDb.collection("users").doc(ALICE_UID).collection("history").doc("hist_alice").set(validHistoryPayload(ALICE_UID, "itm_alice"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceInv = bobDb.collection("users").doc(ALICE_UID).collection("inventory");
  const aliceHist = bobDb.collection("users").doc(ALICE_UID).collection("history");

  await assertFails(aliceInv.get());
  await assertFails(aliceInv.doc("itm_alice").get());
  await assertFails(aliceInv.doc("itm_alice").update({ quantity: 0, updatedAt: new Date(Date.now() - 500) }));
  await assertFails(aliceInv.doc("itm_alice").delete());
  await assertFails(aliceInv.doc("itm_spoof").set(validItemPayload(BOB_UID)));

  await assertFails(aliceHist.get());
  await assertFails(aliceHist.doc("hist_alice").get());
  await assertFails(aliceHist.doc("hist_alice").delete());
});

test("History audit logbook: owner can create/list/delete; updates and invalid types are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const histCol = aliceDb.collection("users").doc(ALICE_UID).collection("history");

  await assertSucceeds(histCol.doc("hist_1").set(validHistoryPayload(ALICE_UID, "itm_1", { type: "IN" })));
  await assertSucceeds(histCol.doc("hist_2").set(validHistoryPayload(ALICE_UID, "itm_1", { type: "OUT", quantity: 3, resultingStock: 21 })));
  await assertSucceeds(histCol.get());

  // Audit entries cannot be mutated
  await assertFails(histCol.doc("hist_1").update({ quantity: 999 }));

  // Invalid enum type rejected
  await assertFails(histCol.doc("hist_bad").set(validHistoryPayload(ALICE_UID, "itm_1", { type: "ADJUST" })));

  // Zero quantity rejected
  await assertFails(histCol.doc("hist_zero").set(validHistoryPayload(ALICE_UID, "itm_1", { quantity: 0 })));
});
