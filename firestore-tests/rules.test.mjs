import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, addDoc, collection, query, where, getDocs,
  arrayUnion, arrayRemove, runTransaction,
} from 'firebase/firestore';

const rulesPath = process.argv[2];
const env = await initializeTestEnvironment({
  projectId: 'spinach-rules-test',
  firestore: { rules: readFileSync(rulesPath, 'utf8'), host: '127.0.0.1', port: 8080 },
});

const DAY = 86400000;
const now = Date.now();
const lottery = (extra) => ({
  campsiteId: 'c1', ownerId: 'owner1', registrationStart: 0, registrationEnd: now + 10 * DAY,
  bookingStart: 0, bookingEnd: 0, winnerCount: 1, waitlist: [], winners: [], losers: [],
  drawn: false, campsiteIds: ['c1'], waitlistCapacity: null, ...extra,
});

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'admins/admin1'), {});
  await setDoc(doc(db, 'campsites/c1'), { name: 'Lake', ownerId: 'owner1', images: ['u1'] });
  await setDoc(doc(db, 'campsites/c2'), { name: 'Pine', ownerId: 'owner1', images: [] });
  await setDoc(doc(db, 'lotteries/L1'), lottery({}));
  await setDoc(doc(db, 'lotteries/L2'), lottery({ waitlist: ['e1', 'e2'], registrationEnd: now - 2 * DAY }));
  await setDoc(doc(db, 'lotteries/L3'), lottery({ drawn: true, winners: ['e1'], losers: ['e2'] }));
  await setDoc(doc(db, 'lotteries/L4'), lottery({ waitlist: ['e1'], waitlistCapacity: 1 }));
  await setDoc(doc(db, 'lotteries/L5'), lottery({}));
  const res = { campsiteId: 'c1', campsiteName: 'Lake', lotteryId: 'L3', ownerId: 'owner1', dates: [], rulesAcknowledged: true, createdAt: 1 };
  await setDoc(doc(db, 'reservations/r1'), { ...res, entrantId: 'e1', status: 'ACTIVE' });
  await setDoc(doc(db, 'reservations/r2'), { ...res, entrantId: 'e1', status: 'ACTIVE' });
  await setDoc(doc(db, 'notifications/n1'), { senderId: 'owner1', recipientId: 'e1', message: 'hi', read: false });
  await setDoc(doc(db, 'messages/m1'), { senderId: 'owner1', recipientId: 'e1', reservationId: 'r1', text: 'hi' });
  await setDoc(doc(db, 'users/e2'), { name: 'E2', notificationsEnabled: true });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const owner = as('owner1'), e1 = as('e1'), e2 = as('e2'), admin = as('admin1');
const anon = env.unauthenticatedContext().firestore();

const checks = [
  ['nobody signed in cannot read campsites', () => assertFails(getDoc(doc(anon, 'campsites/c1')))],
  ['owner registers own campsite', () => assertSucceeds(addDoc(collection(owner, 'campsites'), { name: 'New', ownerId: 'owner1' }))],
  ['cannot register a campsite for someone else', () => assertFails(addDoc(collection(owner, 'campsites'), { name: 'X', ownerId: 'e1' }))],
  ['owner edits own campsite', () => assertSucceeds(updateDoc(doc(owner, 'campsites/c1'), { name: 'Lake 2' }))],
  ['entrant cannot edit campsite', () => assertFails(updateDoc(doc(e1, 'campsites/c1'), { name: 'Mine' }))],
  ['owner cannot give campsite away', () => assertFails(updateDoc(doc(owner, 'campsites/c1'), { ownerId: 'e1' }))],
  ['admin removes an image', () => assertSucceeds(updateDoc(doc(admin, 'campsites/c1'), { images: arrayRemove('u1') }))],
  ['entrant cannot delete campsite', () => assertFails(deleteDoc(doc(e1, 'campsites/c2')))],
  ['entrant searches campsites', () => assertSucceeds(getDocs(collection(e2, 'campsites')))],

  ['owner writes rules', () => assertSucceeds(setDoc(doc(owner, 'campsiteRules/c1'), { ownerId: 'owner1', text: 'No fires', updatedAt: 1 }))],
  ['entrant cannot write rules', () => assertFails(setDoc(doc(e1, 'campsiteRules/c1'), { ownerId: 'e1', text: 'x', updatedAt: 1 }))],
  ['entrant reads rules', () => assertSucceeds(getDoc(doc(e1, 'campsiteRules/c1')))],

  ['owner creates lottery', () => assertSucceeds(addDoc(collection(owner, 'lotteries'), lottery({})))],
  ['cannot create lottery for someone else', () => assertFails(addDoc(collection(e1, 'lotteries'), lottery({})))],
  ['entrant joins (transaction like joinLottery)', () => assertSucceeds(runTransaction(e1, async (tx) => {
    const ref = doc(e1, 'lotteries/L1'); await tx.get(ref); tx.update(ref, { waitlist: arrayUnion('e1') });
  }))],
  ['entrant cannot add someone else', () => assertFails(updateDoc(doc(e1, 'lotteries/L1'), { waitlist: arrayUnion('e9') }))],
  ['entrant cannot touch winners', () => assertFails(updateDoc(doc(e1, 'lotteries/L1'), { winners: ['e1'] }))],
  ['entrant leaves', () => assertSucceeds(updateDoc(doc(e1, 'lotteries/L1'), { waitlist: arrayRemove('e1') }))],
  ['cannot join a full waitlist', () => assertFails(updateDoc(doc(e2, 'lotteries/L4'), { waitlist: arrayUnion('e2') }))],
  ['entrant phone runs a due draw (full set like drawDueLotteries)', () => assertSucceeds(
    setDoc(doc(e2, 'lotteries/L2'), lottery({ registrationEnd: now - 2 * DAY, winners: ['e2'], losers: ['e1'], waitlist: [], drawn: true })))],
  ['cannot draw before registration is over', () => assertFails(
    setDoc(doc(e2, 'lotteries/L5'), lottery({ winners: ['e2'], drawn: true })))],
  ['owner edits own lottery', () => assertSucceeds(updateDoc(doc(owner, 'lotteries/L5'), { winnerCount: 3 }))],
  ['entrant lists lotteries they joined', () => assertSucceeds(getDocs(query(collection(e1, 'lotteries'), where('winners', 'array-contains', 'e1'))))],

  ['winner reserves', () => assertSucceeds(addDoc(collection(e1, 'reservations'), {
    campsiteId: 'c1', lotteryId: 'L3', entrantId: 'e1', ownerId: 'owner1', status: 'ACTIVE', dates: [1], rulesAcknowledged: true, createdAt: 2 }))],
  ['loser cannot reserve', () => assertFails(addDoc(collection(e2, 'reservations'), {
    campsiteId: 'c1', lotteryId: 'L3', entrantId: 'e2', ownerId: 'owner1', status: 'ACTIVE', dates: [1], rulesAcknowledged: true, createdAt: 2 }))],
  ['search reads all active reservations', () => assertSucceeds(getDocs(query(collection(e2, 'reservations'), where('status', '==', 'ACTIVE'))))],
  ['someone else cannot cancel', () => assertFails(updateDoc(doc(e2, 'reservations/r1'), { status: 'CANCELLED' }))],
  ['entrant cancels', () => assertSucceeds(updateDoc(doc(e1, 'reservations/r1'), { status: 'CANCELLED' }))],
  ['entrant cannot reject', () => assertFails(updateDoc(doc(e1, 'reservations/r2'), { status: 'REJECTED' }))],
  ['owner rejects', () => assertSucceeds(updateDoc(doc(owner, 'reservations/r2'), { status: 'REJECTED' }))],

  ['owner sends notification', () => assertSucceeds(addDoc(collection(owner, 'notifications'), { senderId: 'owner1', recipientId: 'e1', message: 'x', read: false }))],
  ['cannot send as someone else', () => assertFails(addDoc(collection(e1, 'notifications'), { senderId: 'owner1', recipientId: 'e2', message: 'x', read: false }))],
  ['owner lists sent notifications', () => assertSucceeds(getDocs(query(collection(owner, 'notifications'), where('senderId', '==', 'owner1'))))],
  ['entrant cannot read the whole log', () => assertFails(getDocs(collection(e1, 'notifications')))],
  ['admin reads the whole log', () => assertSucceeds(getDocs(collection(admin, 'notifications')))],
  ['recipient marks read', () => assertSucceeds(updateDoc(doc(e1, 'notifications/n1'), { read: true }))],

  ['owner messages entrant on own reservation', () => assertSucceeds(addDoc(collection(owner, 'messages'), { senderId: 'owner1', recipientId: 'e1', reservationId: 'r1', text: 'see you' }))],
  ['cannot message on another owner reservation', () => assertFails(addDoc(collection(e2, 'messages'), { senderId: 'e2', recipientId: 'e1', reservationId: 'r1', text: 'x' }))],
  ['admin reads message log', () => assertSucceeds(getDocs(collection(admin, 'messages')))],
  ['entrant cannot read message log', () => assertFails(getDocs(collection(e2, 'messages')))],

  ['user checks own admin doc', () => assertSucceeds(getDoc(doc(e1, 'admins/e1')))],
  ['user cannot read other admin docs', () => assertFails(getDoc(doc(e1, 'admins/admin1')))],
  ['nobody can make themselves admin', () => assertFails(setDoc(doc(e1, 'admins/e1'), {}))],

  ['user writes own profile', () => assertSucceeds(setDoc(doc(e1, 'users/e1'), { name: 'E1', notificationsEnabled: true }))],
  ['user cannot write another profile', () => assertFails(setDoc(doc(e1, 'users/e2'), { name: 'hacked' }))],
  ['owner reads notificationsEnabled', () => assertSucceeds(getDoc(doc(owner, 'users/e2')))],
  ['admin lists profiles', () => assertSucceeds(getDocs(collection(admin, 'users')))],
  ['admin removes a profile', () => assertSucceeds(deleteDoc(doc(admin, 'users/e2')))],
  ['admin removes a campsite', () => assertSucceeds(deleteDoc(doc(admin, 'campsites/c2')))],
  ['admin removes an owner lottery', () => assertSucceeds(deleteDoc(doc(admin, 'lotteries/L5')))],
];

let failed = 0;
for (const [name, run] of checks) {
  try { await run(); console.log('PASS', name); }
  catch (e) { failed++; console.log('FAIL', name, '-', e.message.split('\n')[0]); }
}
console.log(`\n${checks.length - failed}/${checks.length} passed`);
await env.cleanup();
process.exit(failed ? 1 : 0);
