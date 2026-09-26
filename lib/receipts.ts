import { randomBytes } from 'crypto';
import { mkdir, readFile, writeFile } from 'fs/promises';
import path from 'path';
import { bad } from './errors';

const TYPES: Record<string, string> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'application/pdf': 'pdf' };
export const CONTENT_TYPES: Record<string, string> = Object.fromEntries(Object.entries(TYPES).map(([k, v]) => [v, k]));

function dir(): string {
  const d = process.env.EVENLY_DATA_DIR;
  if (!d) throw new Error('EVENLY_DATA_DIR is not set (see .env.local)');
  return path.join(d, 'receipts');
}

// Receipts are served without auth at /r/<name>; the 32-hex random name is the capability.
export async function saveReceipt(file: File): Promise<string> {
  const ext = TYPES[file.type];
  if (!ext) throw bad('Receipt must be a JPEG, PNG, WebP or PDF');
  if (file.size > 15 * 1024 * 1024) throw bad('Receipt is larger than 15 MB');
  await mkdir(dir(), { recursive: true });
  const name = `${randomBytes(16).toString('hex')}.${ext}`;
  await writeFile(path.join(dir(), name), Buffer.from(await file.arrayBuffer()));
  return name;
}

export async function readReceipt(name: string): Promise<{ data: Buffer; type: string } | null> {
  const m = /^([a-f0-9]{32})\.(jpg|png|webp|pdf)$/.exec(name);
  if (!m) return null;
  try {
    return { data: await readFile(path.join(dir(), name)), type: CONTENT_TYPES[m[2]] };
  } catch {
    return null;
  }
}
