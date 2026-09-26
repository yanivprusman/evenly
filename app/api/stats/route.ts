import { route, bad } from '@/lib/http';
import { q } from '@/lib/db';
import { assertCurrency, assertDate, requireGroup } from '@/lib/ledger';

// Spending = my owed share of real expenses (payments are transfers, not spending).
// With ?groupId and ?scope=group, it is the whole group's spending instead.
export const GET = route(async (req, me) => {
  const sp = req.nextUrl.searchParams;
  const currency = assertCurrency(sp.get('currency') ?? me.default_currency);
  const conds = ['e.deleted_at IS NULL', 'e.is_payment = 0', 'e.currency = ?'];
  const params: unknown[] = [currency];
  const groupScope = sp.get('scope') === 'group';
  if (sp.get('groupId')) {
    const gid = Number(sp.get('groupId'));
    await requireGroup(me.id, gid);
    conds.push('e.group_id = ?'); params.push(gid);
  } else if (groupScope) throw bad('scope=group needs groupId');
  if (sp.get('from')) { conds.push('e.date >= ?'); params.push(assertDate(sp.get('from'))); }
  if (sp.get('to')) { conds.push('e.date <= ?'); params.push(assertDate(sp.get('to'))); }
  if (!groupScope) { conds.push('s.user_id = ?'); params.push(me.id); }
  const amount = 's.owed';
  const amtParams: unknown[] = [];
  const from = `FROM expenses e JOIN expense_shares s ON s.expense_id = e.id WHERE ${conds.join(' AND ')}`;
  const byCategory = await q<{ category: string; amount: string }>(
    `SELECT e.category, SUM(${amount}) AS amount ${from} GROUP BY e.category HAVING amount > 0 ORDER BY amount DESC`, [...amtParams, ...params]);
  const byMonth = await q<{ month: string; amount: string }>(
    `SELECT DATE_FORMAT(e.date, '%Y-%m') AS month, SUM(${amount}) AS amount ${from} GROUP BY month ORDER BY month`, [...amtParams, ...params]);
  const cat = byCategory.map((r) => ({ category: r.category, amount: Number(r.amount) }));
  return { currency, byCategory: cat, byMonth: byMonth.map((r) => ({ month: r.month, amount: Number(r.amount) })), total: cat.reduce((a, r) => a + r.amount, 0) };
});
