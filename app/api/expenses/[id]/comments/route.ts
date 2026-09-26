import { route, body, bad, intParam } from '@/lib/http';
import { exec, tx } from '@/lib/db';
import { iso, loadUsers, logActivity, requireExpense } from '@/lib/ledger';
import { q } from '@/lib/db';

export const POST = route<{ id: string }>(async (req, me, p) => {
  const id = intParam(p.id, 'id');
  const e = await requireExpense(me.id, id);
  const b = await body(req);
  const text = typeof b.body === 'string' ? b.body.trim() : '';
  if (!text) throw bad('Comment is empty');
  const cid = await tx(async (c) => {
    const res = await exec('INSERT INTO comments (expense_id, user_id, body, created_at) VALUES (?, ?, ?, UTC_TIMESTAMP())', [id, me.id, text], c);
    await logActivity(c, { actor: me.id, type: 'comment.added', groupId: e.groupId, expenseId: id,
      text: `${me.name} commented on "${e.description}": ${text.slice(0, 120)}`, users: e.shares.map((s) => s.userId) });
    return res.insertId;
  });
  const [row] = await q<{ created_at: string }>('SELECT created_at FROM comments WHERE id = ?', [cid]);
  return { id: cid, expenseId: id, user: (await loadUsers([me.id])).get(me.id), body: text, createdAt: iso(row.created_at) };
});
