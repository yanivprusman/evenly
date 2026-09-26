import { route } from '@/lib/http';
import { myWorld } from '@/lib/ledger';

export const GET = route(async (_req, me) => {
  const w = await myWorld(me.id);
  return { me: w.me, total: w.total, groups: w.groups, friends: w.friends };
});
