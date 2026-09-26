import { route, body, intParam } from '@/lib/http';
import { q } from '@/lib/db';
import { iso, loadUsers, requireExpense, setDeleted, updateExpense } from '@/lib/ledger';

export const GET = route<{ id: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  const e = await requireExpense(me.id, id);
  const comments = await q<{ id: number; user_id: number; body: string; created_at: string }>(
    'SELECT id, user_id, body, created_at FROM comments WHERE expense_id = ? ORDER BY id', [id]);
  const history = await q<{ id: number; actor_id: number; type: string; group_id: number | null; expense_id: number; text: string; created_at: string }>(
    'SELECT id, actor_id, type, group_id, expense_id, text, created_at FROM activity WHERE expense_id = ? ORDER BY id', [id]);
  const users = await loadUsers([...comments.map((c) => c.user_id), ...history.map((h) => h.actor_id)]);
  return {
    ...e,
    comments: comments.map((c) => ({ id: c.id, expenseId: id, user: users.get(c.user_id), body: c.body, createdAt: iso(c.created_at) })),
    history: history.map((h) => ({ id: h.id, actor: users.get(h.actor_id), type: h.type, groupId: h.group_id, expenseId: h.expense_id, text: h.text, createdAt: iso(h.created_at), amountForMe: null })),
  };
});

export const PATCH = route<{ id: string }>(async (req, me, p) => updateExpense(me.id, intParam(p.id, 'id'), await body(req)));

export const DELETE = route<{ id: string }>(async (_req, me, p) => {
  await setDeleted(me.id, intParam(p.id, 'id'), true);
  return { ok: true };
});
