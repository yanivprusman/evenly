import { route, body, bad, intParam } from '@/lib/http';
import { exec, tx } from '@/lib/db';
import { assertCurrency, GROUP_TYPES, GroupType, groupLedger, loadExpenses, loadGroups, logActivity, moneyList, myNetIn, requireGroup } from '@/lib/ledger';

export const GET = route<{ id: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  const group = await requireGroup(me.id, id);
  // Deleted expenses are included (flagged by deletedAt) so the group can show and restore them.
  const expenses = await loadExpenses('e.group_id = ?', [id]);
  const l = groupLedger(group, expenses);
  return {
    group,
    balances: [...l.nets.entries()].map(([userId, m]) => ({ userId, net: moneyList(m) })),
    debts: l.debts,
    myNet: myNetIn(me.id, l),
    expenses,
  };
});

export const PATCH = route<{ id: string }>(async (req, me, p) => {
  const id = intParam(p.id, 'id');
  const g = await requireGroup(me.id, id);
  const b = await body(req);
  const sets: string[] = [], vals: unknown[] = [];
  if (b.name !== undefined) {
    if (typeof b.name !== 'string' || !b.name.trim()) throw bad('name cannot be empty');
    sets.push('name = ?'); vals.push(b.name.trim());
  }
  if (b.type !== undefined) {
    if (!GROUP_TYPES.includes(b.type as GroupType)) throw bad('unknown group type');
    sets.push('type = ?'); vals.push(b.type);
  }
  if (b.defaultCurrency !== undefined) { sets.push('default_currency = ?'); vals.push(assertCurrency(b.defaultCurrency)); }
  if (b.simplifyDebts !== undefined) { sets.push('simplify_debts = ?'); vals.push(b.simplifyDebts ? 1 : 0); }
  if (b.archived !== undefined) { sets.push('archived = ?'); vals.push(b.archived ? 1 : 0); }
  if (!sets.length) return g;
  await tx(async (c) => {
    await exec(`UPDATE \`groups\` SET ${sets.join(', ')} WHERE id = ?`, [...vals, id], c);
    await logActivity(c, { actor: me.id, type: 'group.updated', groupId: id, expenseId: null,
      text: `${me.name} updated the group "${typeof b.name === 'string' ? b.name.trim() : g.name}"`, users: g.members.map((m) => m.id) });
  });
  return (await loadGroups([id])).get(id);
});

export const DELETE = route<{ id: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  const g = await requireGroup(me.id, id);
  const l = groupLedger(g, await loadExpenses('e.group_id = ?', [id]));
  for (const m of l.nets.values()) for (const v of m.values()) if (v) throw bad('Settle up every balance before deleting the group');
  await tx(async (c) => {
    await exec('UPDATE `groups` SET deleted_at = UTC_TIMESTAMP() WHERE id = ?', [id], c);
    await logActivity(c, { actor: me.id, type: 'group.deleted', groupId: id, expenseId: null, text: `${me.name} deleted the group "${g.name}"`, users: g.members.map((m) => m.id) });
  });
  return { ok: true };
});
