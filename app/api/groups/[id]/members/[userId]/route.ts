import { route, bad, intParam } from '@/lib/http';
import { exec, tx } from '@/lib/db';
import { groupLedger, loadExpenses, loadGroups, logActivity, requireGroup } from '@/lib/ledger';

export const DELETE = route<{ id: string; userId: string }>(async (_req, me, p) => {
  const id = intParam(p.id, 'id');
  const userId = intParam(p.userId, 'userId');
  const g = await requireGroup(me.id, id);
  const member = g.members.find((m) => m.id === userId);
  if (!member) throw bad('Not a member');
  const l = groupLedger(g, await loadExpenses('e.group_id = ?', [id]));
  for (const v of (l.nets.get(userId) ?? new Map()).values()) if (v) throw bad(`${member.name} still has a balance in this group`);
  await tx(async (c) => {
    await exec('DELETE FROM group_members WHERE group_id = ? AND user_id = ?', [id, userId], c);
    await logActivity(c, { actor: me.id, type: 'group.member_removed', groupId: id, expenseId: null,
      text: userId === me.id ? `${me.name} left "${g.name}"` : `${me.name} removed ${member.name} from "${g.name}"`, users: g.members.map((m) => m.id) });
  });
  return (await loadGroups([id])).get(id) ?? { ok: true };
});
