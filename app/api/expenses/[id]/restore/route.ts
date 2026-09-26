import { route, intParam } from '@/lib/http';
import { setDeleted } from '@/lib/ledger';

export const POST = route<{ id: string }>(async (_req, me, p) => setDeleted(me.id, intParam(p.id, 'id'), false));
