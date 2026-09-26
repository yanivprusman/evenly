import { route, bad, intParam } from '@/lib/http';
import { exec } from '@/lib/db';
import { loadExpenses, requireExpense } from '@/lib/ledger';
import { saveReceipt } from '@/lib/receipts';

export const POST = route<{ id: string }>(async (req, me, p) => {
  const id = intParam(p.id, 'id');
  await requireExpense(me.id, id);
  const form = await req.formData().catch(() => null);
  const file = form?.get('file');
  if (!(file instanceof File)) throw bad('multipart field "file" is required');
  const name = await saveReceipt(file);
  await exec('UPDATE expenses SET receipt = ?, updated_by = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [name, me.id, id]);
  return (await loadExpenses('e.id = ?', [id]))[0];
});
