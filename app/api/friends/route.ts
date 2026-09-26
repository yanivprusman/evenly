import { route, body } from '@/lib/http';
import { tx } from '@/lib/db';
import { findOrCreateFriend, loadUsers, myWorld } from '@/lib/ledger';

export const GET = route(async (_req, me) => (await myWorld(me.id)).friends);

export const POST = route(async (req, me) => {
  const b = await body(req);
  const id = await tx((c) => findOrCreateFriend(me.id, b, c));
  return (await loadUsers([id])).get(id);
});
