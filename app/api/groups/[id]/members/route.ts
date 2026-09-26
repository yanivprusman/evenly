import { route, body, bad, intParam } from '@/lib/http';
import { exec, tx } from '@/lib/db';
import { befriend, findOrCreateFriend, friendIds, loadGroups, logActivity, requireGroup } from '@/lib/ledger';

export const POST = route<{ id: string }>(async (req, me, p) => {
  const id = intParam(p.id, 'id');
  const g = await requireGroup(me.id, id);
  const b = await body(req);
  await tx(async (c) => {
    let userId: number;
    if (b.userId != null) {
      userId = Number(b.userId);
      if (!(await friendIds(me.id, c)).includes(userId)) throw bad('You can only add your friends');
    } else {
      userId = await findOrCreateFriend(me.id, b, c);
    }
    if (g.members.some((m) => m.id === userId)) throw bad('Already in the group');
    await exec('INSERT INTO group_members (group_id, user_id, added_at) VALUES (?, ?, UTC_TIMESTAMP())', [id, userId], c);
    // Everyone in a group can split with everyone else in it.
    for (const m of g.members) await befriend(m.id, userId, c);
    const [u] = await c.query('SELECT name FROM users WHERE id = ?', [userId]) as unknown as [{ name: string }[]];
    await logActivity(c, { actor: me.id, type: 'group.member_added', groupId: id, expenseId: null,
      text: `${me.name} added ${u[0].name} to "${g.name}"`, users: [...g.members.map((m) => m.id), userId] });
  });
  return (await loadGroups([id])).get(id);
});
