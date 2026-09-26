import { route, bad } from '@/lib/http';
import { groupIdsFor, loadExpenses, loadUsers, requireGroup } from '@/lib/ledger';

const cell = (v: unknown) => {
  const s = v == null ? '' : String(v);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};
const money = (m: number) => (m / 100).toFixed(2);

export const GET = route(async (req, me) => {
  const sp = req.nextUrl.searchParams;
  let expenses;
  let label = 'all';
  if (sp.get('groupId')) {
    const g = await requireGroup(me.id, Number(sp.get('groupId')));
    expenses = await loadExpenses('e.group_id = ? AND e.deleted_at IS NULL', [g.id], undefined, 'e.date, e.id');
    label = g.name;
  } else if (sp.get('friendId')) {
    const fid = Number(sp.get('friendId'));
    if (!Number.isInteger(fid)) throw bad('friendId must be an integer');
    expenses = await loadExpenses(
      'e.deleted_at IS NULL AND e.id IN (SELECT a.expense_id FROM expense_shares a JOIN expense_shares b ON a.expense_id = b.expense_id WHERE a.user_id = ? AND b.user_id = ?)',
      [me.id, fid], undefined, 'e.date, e.id');
    label = `friend-${fid}`;
  } else {
    const gids = await groupIdsFor(me.id);
    expenses = await loadExpenses(
      `e.deleted_at IS NULL AND (e.id IN (SELECT expense_id FROM expense_shares WHERE user_id = ?) ${gids.length ? 'OR e.group_id IN (?)' : ''})`,
      gids.length ? [me.id, gids] : [me.id], undefined, 'e.date, e.id');
  }
  const people = [...new Set(expenses.flatMap((e) => e.shares.map((s) => s.userId)))];
  const users = await loadUsers(people);
  const header = ['Date', 'Description', 'Category', 'Cost', 'Currency', 'Payment', ...people.map((id) => users.get(id)?.name ?? `#${id}`)];
  const lines = [header.map(cell).join(',')];
  for (const e of expenses) {
    const net = new Map(e.shares.map((s) => [s.userId, s.paid - s.owed]));
    lines.push([e.date, e.description, e.category, money(e.cost), e.currency, e.isPayment ? 'yes' : '',
      ...people.map((id) => (net.has(id) ? money(net.get(id)!) : ''))].map(cell).join(','));
  }
  const file = `evenly-${label.replace(/[^\p{L}\p{N}_-]+/gu, '-')}.csv`;
  return new Response('﻿' + lines.join('\n') + '\n', {
    headers: { 'content-type': 'text/csv; charset=utf-8', 'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent(file)}` },
  });
});
