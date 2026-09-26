import { route, body } from '@/lib/http';
import { createExpense } from '@/lib/ledger';

export const POST = route(async (req, me) => createExpense(me.id, await body(req)));
