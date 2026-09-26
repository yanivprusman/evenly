import { route, body, bad } from '@/lib/http';
import { exec, tx } from '@/lib/db';
import { assertCurrency, friendIds, GROUP_TYPES, GroupType, loadGroups, logActivity, myWorld } from '@/lib/ledger';

export const GET = route(async (_req, me) => (await myWorld(me.id)).groups);

export const POST = route(async (req, me) => {
  const b = await body(req);
  const name = typeof b.name === 'string' ? b.name.trim() : '';
  if (!name) throw bad('name is required');
  const type = (b.type ?? 'other') as GroupType;
  if (!GROUP_TYPES.includes(type)) throw bad('unknown group type');
  const currency = b.defaultCurrency ? assertCurrency(b.defaultCurrency) : me.default_currency;
  const memberIds = Array.isArray(b.memberIds) ? b.memberIds.map(Number) : [];
  const friends = new Set(await friendIds(me.id));
  for (const id of memberIds) if (id !== me.id && !friends.has(id)) throw bad('members must be your friends');
  const id = await tx(async (c) => {
    const res = await exec('INSERT INTO `groups` (name, type, default_currency, simplify_debts, created_by, created_at) VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP())',
      [name, type, currency, b.simplifyDebts === false ? 0 : 1, me.id], c);
    const members = [...new Set([me.id, ...memberIds])];
    await exec('INSERT INTO group_members (group_id, user_id, added_at) VALUES ?', [members.map((u) => [res.insertId, u, new Date()])], c);
    await logActivity(c, { actor: me.id, type: 'group.created', groupId: res.insertId, expenseId: null, text: `${me.name} created the group "${name}"`, users: members });
    return res.insertId;
  });
  return (await loadGroups([id])).get(id);
});
