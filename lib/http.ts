import { NextRequest, NextResponse } from 'next/server';
import { ensureSchema, q } from './db';
import { spawnDueRecurring } from './ledger';

import { HttpError, bad } from './errors';
export { HttpError, bad, notFound, forbidden } from './errors';

export interface Me {
  id: number;
  name: string;
  default_currency: string;
}

async function authenticate(req: NextRequest): Promise<Me> {
  const h = req.headers.get('authorization') ?? '';
  const m = /^Bearer\s+([a-f0-9]{40})$/i.exec(h.trim());
  if (!m) throw new HttpError(401, 'Missing bearer token');
  const rows = await q<Me>('SELECT id, name, default_currency FROM users WHERE token = ?', [m[1]]);
  if (!rows.length) throw new HttpError(401, 'Unknown token');
  return rows[0];
}

type Ctx<P> = { params: Promise<P> };

// Wraps an authenticated route: schema, auth, due recurring expenses, uniform errors.
export function route<P = Record<string, string>>(
  fn: (req: NextRequest, me: Me, params: P) => Promise<unknown>,
) {
  return async (req: NextRequest, ctx: Ctx<P>) => {
    try {
      await ensureSchema();
      const me = await authenticate(req);
      await spawnDueRecurring(me.id);
      const out = await fn(req, me, await ctx.params);
      if (out instanceof Response) return out;
      return NextResponse.json(out);
    } catch (e) {
      if (e instanceof HttpError) return NextResponse.json({ error: e.message }, { status: e.status });
      console.error('[evenly]', e);
      return NextResponse.json({ error: e instanceof Error ? e.message : String(e) }, { status: 500 });
    }
  };
}

export async function body<T = Record<string, unknown>>(req: NextRequest): Promise<T> {
  try {
    return (await req.json()) as T;
  } catch {
    throw bad('Body must be JSON');
  }
}

export function intParam(v: string | null | undefined, name: string): number {
  const n = Number(v);
  if (!Number.isInteger(n) || n <= 0) throw bad(`${name} must be a positive integer`);
  return n;
}
