import { route } from '@/lib/http';
import { q } from '@/lib/db';
import { iso, loadUsers, Money } from '@/lib/ledger';

export const GET = route(async (req, me) => {
  const sp = req.nextUrl.searchParams;
  const before = Number(sp.get('before') ?? 0);
  const limit = Math.min(Math.max(Number(sp.get('limit') ?? 50) || 50, 1), 200);
  const rows = await q<{ id: number; actor_id: number; type: string; group_id: number | null; expense_id: number | null; text: string; effects: Record<string, Money> | string | null; created_at: string }>(
    `SELECT a.id, a.actor_id, a.type, a.group_id, a.expense_id, a.text, a.effects, a.created_at
     FROM activity_users au JOIN activity a ON a.id = au.activity_id
     WHERE au.user_id = ? ${before > 0 ? 'AND a.id < ?' : ''} ORDER BY a.id DESC LIMIT ?`,
    before > 0 ? [me.id, before, limit] : [me.id, limit]);
  const users = await loadUsers(rows.map((r) => r.actor_id));
  return rows.map((r) => {
    const eff = typeof r.effects === 'string' ? JSON.parse(r.effects) : r.effects;
    return {
      id: r.id, actor: users.get(r.actor_id), type: r.type, groupId: r.group_id, expenseId: r.expense_id, text: r.text,
      createdAt: iso(r.created_at), amountForMe: eff?.[me.id] && eff[me.id].amount !== 0 ? eff[me.id] : null,
    };
  });
});
