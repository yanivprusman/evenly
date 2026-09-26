import { NextRequest } from 'next/server';
import { readReceipt } from '@/lib/receipts';

export async function GET(_req: NextRequest, ctx: RouteContext<'/r/[name]'>) {
  const { name } = await ctx.params;
  const r = await readReceipt(name);
  if (!r) return new Response('Not found', { status: 404 });
  return new Response(new Uint8Array(r.data), { headers: { 'content-type': r.type, 'cache-control': 'private, max-age=31536000, immutable' } });
}
