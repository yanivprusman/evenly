import { route } from '@/lib/http';
import { groupIdsFor, loadExpenses } from '@/lib/ledger';

export const GET = route(async (req, me) => {
  const term = (req.nextUrl.searchParams.get('q') ?? '').trim();
  if (!term) return [];
  const like = `%${term.replace(/[\\%_]/g, (m) => '\\' + m)}%`;
  const gids = await groupIdsFor(me.id);
  return loadExpenses(
    `e.deleted_at IS NULL
     AND (e.id IN (SELECT expense_id FROM expense_shares WHERE user_id = ?) ${gids.length ? 'OR e.group_id IN (?)' : ''})
     AND (e.description LIKE ? OR e.notes LIKE ? OR e.id IN (SELECT expense_id FROM comments WHERE body LIKE ?))`,
    gids.length ? [me.id, gids, like, like, like] : [me.id, like, like, like]);
});
