import { route, bad, intParam, notFound } from '@/lib/http';
import { exec } from '@/lib/db';
import { friendIds, loadExpenses, myWorld } from '@/lib/ledger';

export const GET = route<{ id: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  const w = await myWorld(me.id);
  const f = w.friends.find((x) => x.friend.id === id);
  if (!f) throw notFound('Not your friend');
  // Every expense we are both part of, in any group, plus the non-group ones.
  const expenses = await loadExpenses(
    `e.id IN (SELECT a.expense_id FROM expense_shares a JOIN expense_shares b ON a.expense_id = b.expense_id WHERE a.user_id = ? AND b.user_id = ?)`,
    [me.id, id]);
  return { friend: f.friend, net: f.net, byGroup: f.byGroup, expenses };
});

export const DELETE = route<{ id: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  if (!(await friendIds(me.id)).includes(id)) throw notFound('Not your friend');
  const w = await myWorld(me.id);
  const f = w.friends.find((x) => x.friend.id === id);
  if (f && f.net.length) throw bad('Settle up before removing this friend');
  const shared = w.groups.filter((g) => g.group.members.some((m) => m.id === id));
  if (shared.length) throw bad(`You still share ${shared.map((g) => `"${g.group.name}"`).join(', ')}`);
  await exec('DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)', [me.id, id, id, me.id]);
  return { ok: true };
});
