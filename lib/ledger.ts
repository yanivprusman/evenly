import type { PoolConnection } from 'mysql2/promise';
import { Conn, exec, q, tx } from './db';
import { bad, forbidden, notFound } from './errors';

// ─── Types ──────────────────────────────────────────────────────────────────

export interface Money { amount: number; currency: string }
export interface Debt { from: number; to: number; amount: number; currency: string }
export interface UserJson { id: number; name: string; email: string | null; phone: string | null; color: string; registered: boolean }
export interface ShareJson { userId: number; paid: number; owed: number; input: number | null }
export interface ExpenseJson {
  id: number; groupId: number | null; description: string; cost: number; currency: string; date: string;
  category: string; notes: string | null; isPayment: boolean; splitType: SplitType; repeat: Repeat;
  shares: ShareJson[]; receiptUrl: string | null; createdBy: number; createdAt: string;
  updatedBy: number | null; updatedAt: string | null; deletedAt: string | null; commentCount: number;
}
export interface GroupJson {
  id: number; name: string; type: GroupType; defaultCurrency: string; simplifyDebts: boolean;
  members: UserJson[]; createdAt: string; archived: boolean;
}

export type SplitType = 'equal' | 'exact' | 'percent' | 'shares' | 'adjustment';
export type Repeat = 'none' | 'weekly' | 'biweekly' | 'monthly' | 'yearly';
export type GroupType = 'home' | 'trip' | 'couple' | 'other';
const SPLIT_TYPES: SplitType[] = ['equal', 'exact', 'percent', 'shares', 'adjustment'];
const REPEATS: Repeat[] = ['none', 'weekly', 'biweekly', 'monthly', 'yearly'];
export const GROUP_TYPES: GroupType[] = ['home', 'trip', 'couple', 'other'];

// ─── Small helpers ──────────────────────────────────────────────────────────

const PALETTE = ['#0F766E', '#C2410C', '#7C3AED', '#0369A1', '#B45309', '#BE185D', '#15803D', '#4338CA', '#A16207', '#0E7490'];
export const colorFor = (seed: number) => PALETTE[seed % PALETTE.length];

export const iso = (s: string | null): string | null => (s ? s.replace(' ', 'T') + 'Z' : null);

export function assertCurrency(c: unknown): string {
  if (typeof c !== 'string' || !/^[A-Z]{3}$/.test(c)) throw bad('currency must be a 3-letter ISO code');
  return c;
}

export function assertDate(d: unknown): string {
  if (typeof d !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d) || isNaN(Date.parse(d + 'T00:00:00Z'))) {
    throw bad('date must be YYYY-MM-DD');
  }
  return d;
}

function addMoney(map: Map<string, number>, currency: string, amount: number) {
  map.set(currency, (map.get(currency) ?? 0) + amount);
}
export function moneyList(map: Map<string, number>): Money[] {
  return [...map.entries()].filter(([, a]) => a !== 0).map(([currency, amount]) => ({ amount, currency }))
    .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount));
}

// ─── Users & friends ────────────────────────────────────────────────────────

interface UserRow { id: number; name: string; email: string | null; phone: string | null; color: string; token: string | null }
export const userJson = (u: UserRow): UserJson => ({
  id: u.id, name: u.name, email: u.email, phone: u.phone, color: u.color, registered: !!u.token,
});

export async function loadUsers(ids: number[], c?: Conn): Promise<Map<number, UserJson>> {
  const out = new Map<number, UserJson>();
  const uniq = [...new Set(ids)];
  if (!uniq.length) return out;
  const rows = await q<UserRow>('SELECT id, name, email, phone, color, token FROM users WHERE id IN (?)', [uniq], c);
  for (const r of rows) out.set(r.id, userJson(r));
  return out;
}

export async function friendIds(me: number, c?: Conn): Promise<number[]> {
  const rows = await q<{ friend_id: number }>('SELECT friend_id FROM friendships WHERE user_id = ?', [me], c);
  return rows.map((r) => r.friend_id);
}

export async function befriend(a: number, b: number, c?: Conn) {
  if (a === b) return;
  await exec('INSERT IGNORE INTO friendships (user_id, friend_id, created_at) VALUES (?, ?, UTC_TIMESTAMP()), (?, ?, UTC_TIMESTAMP())', [a, b, b, a], c);
}

export function normalizePhone(p: unknown): string | null {
  if (p == null || p === '') return null;
  if (typeof p !== 'string') throw bad('phone must be a string');
  const digits = p.replace(/[^\d+]/g, '');
  if (digits.length < 7) throw bad('phone looks too short');
  return digits;
}

// Finds an existing user by email/phone or creates a placeholder, and makes them my friend.
export async function findOrCreateFriend(me: number, input: { name?: unknown; email?: unknown; phone?: unknown }, c?: Conn): Promise<number> {
  const name = typeof input.name === 'string' ? input.name.trim() : '';
  const email = typeof input.email === 'string' && input.email.trim() ? input.email.trim().toLowerCase() : null;
  const phone = normalizePhone(input.phone);
  let id: number | null = null;
  if (email) id = (await q<{ id: number }>('SELECT id FROM users WHERE email = ? LIMIT 1', [email], c))[0]?.id ?? null;
  if (!id && phone) id = (await q<{ id: number }>('SELECT id FROM users WHERE phone = ? LIMIT 1', [phone], c))[0]?.id ?? null;
  if (!id) {
    if (!name) throw bad('name is required for a new friend');
    const res = await exec('INSERT INTO users (name, email, phone, color, created_by, created_at) VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP())',
      [name, email, phone, '#000000', me], c);
    id = res.insertId;
    await exec('UPDATE users SET color = ? WHERE id = ?', [colorFor(id), id], c);
  }
  if (id === me) throw bad('That is you');
  await befriend(me, id, c);
  return id;
}

// ─── Groups ─────────────────────────────────────────────────────────────────

interface GroupRow { id: number; name: string; type: GroupType; default_currency: string; simplify_debts: number; archived: number; created_at: string }

export async function groupIdsFor(me: number, c?: Conn): Promise<number[]> {
  const rows = await q<{ group_id: number }>(
    'SELECT gm.group_id FROM group_members gm JOIN `groups` g ON g.id = gm.group_id WHERE gm.user_id = ? AND g.deleted_at IS NULL', [me], c);
  return rows.map((r) => r.group_id);
}

export async function loadGroups(ids: number[], c?: Conn): Promise<Map<number, GroupJson>> {
  const out = new Map<number, GroupJson>();
  if (!ids.length) return out;
  const rows = await q<GroupRow>('SELECT id, name, type, default_currency, simplify_debts, archived, created_at FROM `groups` WHERE id IN (?) AND deleted_at IS NULL', [ids], c);
  const mem = await q<{ group_id: number; user_id: number }>('SELECT group_id, user_id FROM group_members WHERE group_id IN (?) ORDER BY added_at, user_id', [ids], c);
  const users = await loadUsers(mem.map((m) => m.user_id), c);
  for (const r of rows) {
    out.set(r.id, {
      id: r.id, name: r.name, type: r.type, defaultCurrency: r.default_currency, simplifyDebts: !!r.simplify_debts,
      archived: !!r.archived, createdAt: iso(r.created_at)!,
      members: mem.filter((m) => m.group_id === r.id).map((m) => users.get(m.user_id)!).filter(Boolean),
    });
  }
  return out;
}

export async function requireGroup(me: number, groupId: number, c?: Conn): Promise<GroupJson> {
  const g = (await loadGroups([groupId], c)).get(groupId);
  if (!g) throw notFound('Group not found');
  if (!g.members.some((m) => m.id === me)) throw forbidden('You are not in this group');
  return g;
}

// ─── Expenses ───────────────────────────────────────────────────────────────

interface ExpenseRow {
  id: number; group_id: number | null; description: string; cost: number; currency: string; date: string;
  category: string; notes: string | null; is_payment: number; split_type: SplitType; repeat: Repeat;
  receipt: string | null; created_by: number; created_at: string; updated_by: number | null;
  updated_at: string | null; deleted_at: string | null;
}
interface ShareRow { expense_id: number; user_id: number; paid: number; owed: number; input: number | null }

const EXPENSE_COLS = 'e.id, e.group_id, e.description, e.cost, e.currency, DATE_FORMAT(e.date, "%Y-%m-%d") AS date, e.category, e.notes, e.is_payment, e.split_type, e.`repeat`, e.receipt, e.created_by, e.created_at, e.updated_by, e.updated_at, e.deleted_at';

export async function loadExpenses(where: string, params: unknown[], c?: Conn, order = 'e.date DESC, e.id DESC'): Promise<ExpenseJson[]> {
  const rows = await q<ExpenseRow>(`SELECT ${EXPENSE_COLS} FROM expenses e WHERE ${where} ORDER BY ${order}`, params, c);
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id);
  const shares = await q<ShareRow>('SELECT expense_id, user_id, paid, owed, input FROM expense_shares WHERE expense_id IN (?) ORDER BY user_id', [ids], c);
  const counts = await q<{ expense_id: number; n: number }>('SELECT expense_id, COUNT(*) AS n FROM comments WHERE expense_id IN (?) GROUP BY expense_id', [ids], c);
  const byExp = new Map<number, ShareJson[]>();
  for (const s of shares) {
    const list = byExp.get(s.expense_id) ?? [];
    list.push({ userId: s.user_id, paid: Number(s.paid), owed: Number(s.owed), input: s.input });
    byExp.set(s.expense_id, list);
  }
  const cnt = new Map(counts.map((r) => [r.expense_id, Number(r.n)]));
  return rows.map((r) => ({
    id: r.id, groupId: r.group_id, description: r.description, cost: Number(r.cost), currency: r.currency, date: r.date,
    category: r.category, notes: r.notes, isPayment: !!r.is_payment, splitType: r.split_type, repeat: r.repeat,
    shares: byExp.get(r.id) ?? [], receiptUrl: r.receipt ? `/r/${r.receipt}` : null, createdBy: r.created_by,
    createdAt: iso(r.created_at)!, updatedBy: r.updated_by, updatedAt: iso(r.updated_at), deletedAt: iso(r.deleted_at),
    commentCount: cnt.get(r.id) ?? 0,
  }));
}

export async function requireExpense(me: number, id: number, c?: Conn): Promise<ExpenseJson> {
  const e = (await loadExpenses('e.id = ?', [id], c))[0];
  if (!e) throw notFound('Expense not found');
  if (e.groupId != null) {
    const inGroup = await q('SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?', [e.groupId, me], c);
    if (!inGroup.length && !e.shares.some((s) => s.userId === me)) throw forbidden();
  } else if (!e.shares.some((s) => s.userId === me)) {
    throw forbidden();
  }
  return e;
}

export interface ExpenseInput {
  groupId: number | null; description: string; cost: number; currency: string; date: string; category: string;
  notes: string | null; isPayment: boolean; splitType: SplitType; repeat: Repeat; shares: ShareJson[];
}

const isInt = (n: unknown): n is number => typeof n === 'number' && Number.isSafeInteger(n);

export async function validateExpense(me: number, raw: Record<string, unknown>, c?: Conn): Promise<ExpenseInput> {
  const groupId = raw.groupId == null ? null : Number(raw.groupId);
  if (groupId !== null && !Number.isInteger(groupId)) throw bad('groupId must be an integer or null');
  const description = typeof raw.description === 'string' ? raw.description.trim() : '';
  if (!description) throw bad('description is required');
  if (description.length > 255) throw bad('description is too long');
  if (!isInt(raw.cost) || raw.cost <= 0) throw bad('cost must be a positive integer in minor units');
  const cost = raw.cost;
  const currency = assertCurrency(raw.currency);
  const date = assertDate(raw.date);
  const category = typeof raw.category === 'string' && /^[a-z]+(\.[a-z]+)?$/.test(raw.category) ? raw.category : 'general';
  const notes = typeof raw.notes === 'string' && raw.notes.trim() ? raw.notes.trim() : null;
  const isPayment = raw.isPayment === true;
  const splitType = (raw.splitType ?? 'equal') as SplitType;
  if (!SPLIT_TYPES.includes(splitType)) throw bad('unknown splitType');
  const repeat = (raw.repeat ?? 'none') as Repeat;
  if (!REPEATS.includes(repeat)) throw bad('unknown repeat');
  if (!Array.isArray(raw.shares) || raw.shares.length === 0) throw bad('shares are required');

  const shares: ShareJson[] = [];
  const seen = new Set<number>();
  for (const s of raw.shares as Record<string, unknown>[]) {
    const userId = Number(s.userId);
    if (!Number.isInteger(userId)) throw bad('share.userId must be an integer');
    if (seen.has(userId)) throw bad('a person appears twice in shares');
    seen.add(userId);
    const paid = s.paid ?? 0, owed = s.owed ?? 0;
    if (!isInt(paid) || !isInt(owed) || paid < 0 || owed < 0) throw bad('share paid/owed must be non-negative integers');
    const input = s.input == null ? null : Number(s.input);
    if (input !== null && !Number.isFinite(input)) throw bad('share.input must be a number');
    if (paid === 0 && owed === 0) continue; // not involved
    shares.push({ userId, paid, owed, input });
  }
  const sumPaid = shares.reduce((a, s) => a + s.paid, 0);
  const sumOwed = shares.reduce((a, s) => a + s.owed, 0);
  if (sumPaid !== cost) throw bad(`paid amounts add up to ${sumPaid}, not ${cost}`);
  if (sumOwed !== cost) throw bad(`owed amounts add up to ${sumOwed}, not ${cost}`);
  if (isPayment) {
    const payer = shares.filter((s) => s.paid > 0), payee = shares.filter((s) => s.owed > 0);
    if (shares.length !== 2 || payer.length !== 1 || payee.length !== 1 || payer[0].userId === payee[0].userId) {
      throw bad('a payment is one person paying another');
    }
  }

  const ids = shares.map((s) => s.userId);
  if (groupId !== null) {
    const g = await requireGroup(me, groupId, c);
    const members = new Set(g.members.map((m) => m.id));
    for (const id of ids) if (!members.has(id)) throw bad('everyone in the split must be a member of the group');
  } else {
    if (!ids.includes(me)) throw bad('you must be part of a non-group expense');
    const friends = new Set(await friendIds(me, c));
    for (const id of ids) if (id !== me && !friends.has(id)) throw bad('everyone in the split must be your friend');
  }
  return { groupId, description, cost, currency, date, category, notes, isPayment, splitType, repeat, shares };
}

// ─── Activity ───────────────────────────────────────────────────────────────

export const fmt = (m: number, currency: string) => {
  const sym: Record<string, string> = { ILS: '₪', USD: '$', EUR: '€', GBP: '£', JPY: '¥' };
  const s = (Math.abs(m) / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return (sym[currency] ?? currency + ' ') + s;
};

export async function logActivity(c: Conn, a: {
  actor: number; type: string; groupId: number | null; expenseId: number | null; text: string;
  effects?: Record<number, Money>; users: number[];
}) {
  const res = await exec('INSERT INTO activity (actor_id, type, group_id, expense_id, text, effects, created_at) VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP())',
    [a.actor, a.type, a.groupId, a.expenseId, a.text, a.effects ? JSON.stringify(a.effects) : null], c);
  const users = [...new Set([...a.users, a.actor])];
  await exec('INSERT IGNORE INTO activity_users (activity_id, user_id) VALUES ?', [users.map((u) => [res.insertId, u])], c);
  return res.insertId;
}

function effectsOf(e: { shares: ShareJson[]; currency: string }, sign = 1): Record<number, Money> {
  const out: Record<number, Money> = {};
  for (const s of e.shares) out[s.userId] = { amount: sign * (s.paid - s.owed), currency: e.currency };
  return out;
}

async function audience(c: Conn, e: { groupId: number | null; shares: ShareJson[] }): Promise<number[]> {
  const ids = e.shares.map((s) => s.userId);
  if (e.groupId != null) {
    const mem = await q<{ user_id: number }>('SELECT user_id FROM group_members WHERE group_id = ?', [e.groupId], c);
    ids.push(...mem.map((m) => m.user_id));
  }
  return ids;
}

async function where(c: Conn, groupId: number | null) {
  if (groupId == null) return '';
  const g = await q<{ name: string }>('SELECT name FROM `groups` WHERE id = ?', [groupId], c);
  return g.length ? ` in "${g[0].name}"` : '';
}

async function nameOf(c: Conn, id: number) {
  return (await q<{ name: string }>('SELECT name FROM users WHERE id = ?', [id], c))[0]?.name ?? 'Someone';
}

async function describe(c: Conn, verb: string, actor: number, e: ExpenseInput | ExpenseJson) {
  const who = await nameOf(c, actor);
  if (e.isPayment) {
    const payer = e.shares.find((s) => s.paid > 0)!, payee = e.shares.find((s) => s.owed > 0)!;
    return `${who} ${verb} a payment: ${await nameOf(c, payer.userId)} paid ${await nameOf(c, payee.userId)} ${fmt(e.cost, e.currency)}${await where(c, e.groupId)}`;
  }
  return `${who} ${verb} "${e.description}"${await where(c, e.groupId)}`;
}

// ─── Writes ─────────────────────────────────────────────────────────────────

async function writeShares(c: PoolConnection, expenseId: number, shares: ShareJson[]) {
  await exec('DELETE FROM expense_shares WHERE expense_id = ?', [expenseId], c);
  await exec('INSERT INTO expense_shares (expense_id, user_id, paid, owed, input) VALUES ?',
    [shares.map((s) => [expenseId, s.userId, s.paid, s.owed, s.input])], c);
}

export async function insertExpense(c: PoolConnection, me: number, e: ExpenseInput, opts: { source?: string; sourceKey?: string; createdAt?: string; quiet?: boolean } = {}): Promise<number> {
  const res = await exec(
    `INSERT INTO expenses (group_id, description, cost, currency, date, category, notes, is_payment, split_type, \`repeat\`, source, source_key, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ${opts.createdAt ? '?' : 'UTC_TIMESTAMP()'})`,
    [e.groupId, e.description, e.cost, e.currency, e.date, e.category, e.notes, e.isPayment ? 1 : 0, e.splitType, e.repeat,
      opts.source ?? null, opts.sourceKey ?? null, me, ...(opts.createdAt ? [opts.createdAt] : [])], c);
  await writeShares(c, res.insertId, e.shares);
  if (!opts.quiet) {
    await logActivity(c, {
      actor: me, type: e.isPayment ? 'payment.added' : 'expense.added', groupId: e.groupId, expenseId: res.insertId,
      text: await describe(c, 'added', me, e), effects: effectsOf(e), users: await audience(c, e),
    });
  }
  return res.insertId;
}

export async function createExpense(me: number, raw: Record<string, unknown>): Promise<ExpenseJson> {
  const id = await tx(async (c) => insertExpense(c, me, await validateExpense(me, raw, c)));
  return (await loadExpenses('e.id = ?', [id]))[0];
}

export async function updateExpense(me: number, id: number, raw: Record<string, unknown>): Promise<ExpenseJson> {
  await tx(async (c) => {
    const old = await requireExpense(me, id, c);
    if (old.deletedAt) throw bad('Restore the expense before editing it');
    const merged = { ...old, ...raw } as Record<string, unknown>;
    const e = await validateExpense(me, merged, c);
    await exec(
      `UPDATE expenses SET group_id=?, description=?, cost=?, currency=?, date=?, category=?, notes=?, is_payment=?, split_type=?, \`repeat\`=?, updated_by=?, updated_at=UTC_TIMESTAMP() WHERE id=?`,
      [e.groupId, e.description, e.cost, e.currency, e.date, e.category, e.notes, e.isPayment ? 1 : 0, e.splitType, e.repeat, me, id], c);
    await writeShares(c, id, e.shares);
    const changes = diffText(old, e);
    await logActivity(c, {
      actor: me, type: e.isPayment ? 'payment.updated' : 'expense.updated', groupId: e.groupId, expenseId: id,
      text: (await describe(c, 'updated', me, e)) + (changes ? ` — ${changes}` : ''), effects: effectsOf(e),
      users: [...await audience(c, e), ...await audience(c, old)],
    });
  });
  return (await loadExpenses('e.id = ?', [id]))[0];
}

function diffText(old: ExpenseJson, e: ExpenseInput): string {
  const parts: string[] = [];
  if (old.description !== e.description) parts.push(`description "${old.description}" → "${e.description}"`);
  if (old.cost !== e.cost || old.currency !== e.currency) parts.push(`cost ${fmt(old.cost, old.currency)} → ${fmt(e.cost, e.currency)}`);
  if (old.date !== e.date) parts.push(`date ${old.date} → ${e.date}`);
  if (old.category !== e.category) parts.push('category changed');
  if ((old.notes ?? '') !== (e.notes ?? '')) parts.push('notes changed');
  const key = (s: ShareJson[]) => JSON.stringify([...s].sort((a, b) => a.userId - b.userId).map((x) => [x.userId, x.paid, x.owed]));
  if (key(old.shares) !== key(e.shares)) parts.push('split changed');
  return parts.join('; ');
}

export async function setDeleted(me: number, id: number, deleted: boolean): Promise<ExpenseJson> {
  await tx(async (c) => {
    const e = await requireExpense(me, id, c);
    if (!!e.deletedAt === deleted) return;
    if (deleted) await exec('UPDATE expenses SET deleted_at = UTC_TIMESTAMP(), deleted_by = ? WHERE id = ?', [me, id], c);
    else await exec('UPDATE expenses SET deleted_at = NULL, deleted_by = NULL WHERE id = ?', [id], c);
    await logActivity(c, {
      actor: me, type: deleted ? 'expense.deleted' : 'expense.restored', groupId: e.groupId, expenseId: id,
      text: await describe(c, deleted ? 'deleted' : 'restored', me, e), effects: effectsOf(e, deleted ? -1 : 1),
      users: await audience(c, e),
    });
  });
  return (await loadExpenses('e.id = ?', [id]))[0];
}

// ─── Recurring ──────────────────────────────────────────────────────────────

export function nextDate(date: string, repeat: Repeat): string {
  const d = new Date(date + 'T00:00:00Z');
  const day = d.getUTCDate();
  switch (repeat) {
    case 'weekly': d.setUTCDate(day + 7); break;
    case 'biweekly': d.setUTCDate(day + 14); break;
    case 'monthly': case 'yearly': {
      const y = d.getUTCFullYear() + (repeat === 'yearly' ? 1 : 0);
      const m = d.getUTCMonth() + (repeat === 'monthly' ? 1 : 0);
      const last = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
      return new Date(Date.UTC(y, m, Math.min(day, last))).toISOString().slice(0, 10);
    }
    default: throw new Error('not recurring');
  }
  return d.toISOString().slice(0, 10);
}

export async function spawnDueRecurring(me: number) {
  const today = new Date().toISOString().slice(0, 10);
  const due = await q<{ id: number }>(
    `SELECT DISTINCT e.id FROM expenses e JOIN expense_shares s ON s.expense_id = e.id
     WHERE s.user_id = ? AND e.\`repeat\` <> 'none' AND e.repeat_spawned = 0 AND e.deleted_at IS NULL`, [me]);
  for (const { id } of due) {
    await tx(async (c) => {
      const locked = await q('SELECT id FROM expenses WHERE id = ? AND repeat_spawned = 0 FOR UPDATE', [id], c);
      if (!locked.length) return;
      let cur: ExpenseJson | undefined = (await loadExpenses('e.id = ?', [id], c))[0];
      while (cur && nextDate(cur.date, cur.repeat) <= today) {
        const next: ExpenseInput = { ...cur, date: nextDate(cur.date, cur.repeat) };
        const newId = await insertExpense(c, cur.createdBy, next);
        await exec('UPDATE expenses SET repeat_spawned = 1 WHERE id = ?', [cur.id], c);
        cur = (await loadExpenses('e.id = ?', [newId], c))[0];
      }
    });
  }
}

// ─── Balances ───────────────────────────────────────────────────────────────

// Pairwise debts produced by one expense: every debtor owes each payer in proportion to what
// that payer fronted. Keyed "currency|lowId|highId", value = what lowId owes highId (signed).
type Pairwise = Map<string, number>;

function addPair(p: Pairwise, currency: string, from: number, to: number, amount: number) {
  if (from === to || amount === 0) return;
  const [a, b, sign] = from < to ? [from, to, 1] : [to, from, -1];
  const k = `${currency}|${a}|${b}`;
  p.set(k, (p.get(k) ?? 0) + sign * amount);
}

function pairwiseOf(e: ExpenseJson, p: Pairwise) {
  const nets = e.shares.map((s) => ({ id: s.userId, n: s.paid - s.owed }));
  const creditors = nets.filter((x) => x.n > 0), debtors = nets.filter((x) => x.n < 0);
  const total = creditors.reduce((a, x) => a + x.n, 0);
  if (!total) return;
  for (const d of debtors) {
    const owe = -d.n;
    // largest-remainder so this debtor's pieces add up to exactly what they owe
    const raw = creditors.map((cr) => ({ id: cr.id, exact: (owe * cr.n) / total }));
    const parts = raw.map((r) => ({ id: r.id, amt: Math.floor(r.exact), rem: r.exact - Math.floor(r.exact) }));
    let left = owe - parts.reduce((a, x) => a + x.amt, 0);
    for (const x of [...parts].sort((a, b) => b.rem - a.rem)) { if (left <= 0) break; x.amt++; left--; }
    for (const x of parts) addPair(p, e.currency, d.id, x.id, x.amt);
  }
}

function pairsToDebts(p: Pairwise): Debt[] {
  const out: Debt[] = [];
  for (const [k, v] of p) {
    if (!v) continue;
    const [currency, a, b] = k.split('|');
    out.push(v > 0 ? { from: +a, to: +b, amount: v, currency } : { from: +b, to: +a, amount: -v, currency });
  }
  return out;
}

// Fewest transfers that settle everyone: repeatedly match the largest debtor with the largest creditor.
export function simplify(nets: Map<number, Map<string, number>>): Debt[] {
  const out: Debt[] = [];
  const currencies = new Set<string>();
  for (const m of nets.values()) for (const c of m.keys()) currencies.add(c);
  for (const currency of currencies) {
    const bal = [...nets.entries()].map(([id, m]) => ({ id, n: m.get(currency) ?? 0 })).filter((x) => x.n !== 0);
    for (;;) {
      const cr = bal.filter((x) => x.n > 0).sort((a, b) => b.n - a.n)[0];
      const de = bal.filter((x) => x.n < 0).sort((a, b) => a.n - b.n)[0];
      if (!cr || !de) break;
      const amt = Math.min(cr.n, -de.n);
      out.push({ from: de.id, to: cr.id, amount: amt, currency });
      cr.n -= amt; de.n += amt;
    }
  }
  return out;
}

export interface GroupLedger { nets: Map<number, Map<string, number>>; debts: Debt[] }

export function groupLedger(group: GroupJson, expenses: ExpenseJson[]): GroupLedger {
  const nets = new Map<number, Map<string, number>>();
  for (const m of group.members) nets.set(m.id, new Map());
  const pairs: Pairwise = new Map();
  for (const e of expenses) {
    if (e.deletedAt) continue;
    for (const s of e.shares) {
      if (!nets.has(s.userId)) nets.set(s.userId, new Map());
      addMoney(nets.get(s.userId)!, e.currency, s.paid - s.owed);
    }
    pairwiseOf(e, pairs);
  }
  return { nets, debts: group.simplifyDebts ? simplify(nets) : pairsToDebts(pairs) };
}

// Non-group expenses between me and others, as debts.
export function nonGroupDebts(expenses: ExpenseJson[]): Debt[] {
  const pairs: Pairwise = new Map();
  for (const e of expenses) if (!e.deletedAt) pairwiseOf(e, pairs);
  return pairsToDebts(pairs);
}

// What `other` owes me (positive) across a list of debts.
export function netWith(me: number, other: number, debts: Debt[]): Map<string, number> {
  const m = new Map<string, number>();
  for (const d of debts) {
    if (d.from === other && d.to === me) addMoney(m, d.currency, d.amount);
    if (d.from === me && d.to === other) addMoney(m, d.currency, -d.amount);
  }
  return m;
}

export function myNetIn(me: number, l: GroupLedger): Money[] {
  return moneyList(l.nets.get(me) ?? new Map());
}

// Everything the dashboard/friends views need, computed from one read of my data.
export async function myWorld(me: number) {
  const gids = await groupIdsFor(me);
  const groups = await loadGroups(gids);
  const groupExpenses = gids.length ? await loadExpenses('e.group_id IN (?) AND e.deleted_at IS NULL', [gids]) : [];
  const nonGroup = await loadExpenses(
    'e.group_id IS NULL AND e.deleted_at IS NULL AND e.id IN (SELECT expense_id FROM expense_shares WHERE user_id = ?)', [me]);
  const ledgers = new Map<number, GroupLedger>();
  const lastActivity = new Map<number, string>();
  for (const g of groups.values()) {
    const ex = groupExpenses.filter((e) => e.groupId === g.id);
    ledgers.set(g.id, groupLedger(g, ex));
    const last = ex.reduce((a, e) => ((e.updatedAt ?? e.createdAt) > a ? (e.updatedAt ?? e.createdAt) : a), g.createdAt);
    lastActivity.set(g.id, last);
  }
  const ng = nonGroupDebts(nonGroup);
  const fids = await friendIds(me);
  const people = new Set<number>(fids);
  for (const g of groups.values()) for (const m of g.members) if (m.id !== me) people.add(m.id);
  const users = await loadUsers([...people, me]);

  const friends = [...people].map((fid) => {
    const total = new Map<string, number>();
    const byGroup: { groupId: number | null; name: string; net: Money[] }[] = [];
    for (const g of groups.values()) {
      const n = netWith(me, fid, ledgers.get(g.id)!.debts);
      if ([...n.values()].some((v) => v)) {
        byGroup.push({ groupId: g.id, name: g.name, net: moneyList(n) });
        for (const [c, v] of n) addMoney(total, c, v);
      }
    }
    const n = netWith(me, fid, ng);
    if ([...n.values()].some((v) => v)) {
      byGroup.push({ groupId: null, name: 'Non-group expenses', net: moneyList(n) });
      for (const [c, v] of n) addMoney(total, c, v);
    }
    return { friend: users.get(fid)!, net: moneyList(total), byGroup };
  }).filter((f) => f.friend).sort((a, b) => a.friend.name.localeCompare(b.friend.name));

  const total = new Map<string, number>();
  for (const f of friends) for (const m of f.net) addMoney(total, m.currency, m.amount);

  const groupSummaries = [...groups.values()].map((g) => {
    const l = ledgers.get(g.id)!;
    return {
      group: g, myNet: myNetIn(me, l), debts: l.debts.filter((d) => d.from === me || d.to === me),
      lastActivityAt: lastActivity.get(g.id)!,
    };
  }).sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt));

  return { me: users.get(me)!, total: moneyList(total), groups: groupSummaries, friends, ledgers, groupsById: groups, nonGroup };
}
