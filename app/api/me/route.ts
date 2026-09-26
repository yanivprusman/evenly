import { route, body, bad } from '@/lib/http';
import { exec, q } from '@/lib/db';
import { assertCurrency, normalizePhone } from '@/lib/ledger';

async function load(id: number) {
  const [u] = await q<{ id: number; name: string; email: string | null; phone: string | null; color: string; default_currency: string }>(
    'SELECT id, name, email, phone, color, default_currency FROM users WHERE id = ?', [id]);
  return { id: u.id, name: u.name, email: u.email, phone: u.phone, color: u.color, registered: true, defaultCurrency: u.default_currency };
}

export const GET = route(async (_req, me) => load(me.id));

export const PATCH = route(async (req, me) => {
  const b = await body(req);
  if (b.name !== undefined) {
    if (typeof b.name !== 'string' || !b.name.trim()) throw bad('name cannot be empty');
    await exec('UPDATE users SET name = ? WHERE id = ?', [b.name.trim(), me.id]);
  }
  if (b.email !== undefined) {
    const email = typeof b.email === 'string' && b.email.trim() ? b.email.trim().toLowerCase() : null;
    await exec('UPDATE users SET email = ? WHERE id = ?', [email, me.id]);
  }
  if (b.phone !== undefined) await exec('UPDATE users SET phone = ? WHERE id = ?', [normalizePhone(b.phone), me.id]);
  if (b.defaultCurrency !== undefined) await exec('UPDATE users SET default_currency = ? WHERE id = ?', [assertCurrency(b.defaultCurrency), me.id]);
  return load(me.id);
});
