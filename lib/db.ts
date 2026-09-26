import mysql from 'mysql2/promise';
import { SCHEMA } from './schema';

// The ledger lives in the shared MySQL (3308): the NUC's own instance on the leader, and
// mysql-router to it on every other peer — so dev and prod read the same cross-peer store.
function required(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`${name} is not set (see .env.local)`);
  return v;
}

const globalForDb = globalThis as typeof globalThis & {
  _evenlyPool?: mysql.Pool;
  _evenlySchema?: Promise<void>;
};

function pool(): mysql.Pool {
  if (!globalForDb._evenlyPool) {
    globalForDb._evenlyPool = mysql.createPool({
      host: required('DB_HOST'),
      port: Number(required('DB_PORT')),
      user: required('DB_USER'),
      password: required('DB_PASSWORD'),
      database: required('DB_NAME'),
      waitForConnections: true,
      connectionLimit: 8,
      charset: 'utf8mb4',
      dateStrings: true,
      timezone: 'Z',
      supportBigNumbers: true,
      bigNumberStrings: false,
    });
  }
  return globalForDb._evenlyPool;
}

export type Conn = mysql.Pool | mysql.PoolConnection;

export async function ensureSchema(): Promise<void> {
  if (!globalForDb._evenlySchema) {
    globalForDb._evenlySchema = (async () => {
      for (const stmt of SCHEMA) await pool().query(stmt);
    })().catch((e) => {
      globalForDb._evenlySchema = undefined;
      throw e;
    });
  }
  return globalForDb._evenlySchema;
}

export async function q<T = Record<string, unknown>>(sql: string, params?: unknown[], c: Conn = pool()): Promise<T[]> {
  const [rows] = await c.query(sql, params);
  return rows as T[];
}

export async function exec(sql: string, params?: unknown[], c: Conn = pool()) {
  const [res] = await c.query(sql, params);
  return res as mysql.ResultSetHeader;
}

export async function tx<T>(fn: (c: mysql.PoolConnection) => Promise<T>): Promise<T> {
  const c = await pool().getConnection();
  try {
    await c.beginTransaction();
    const out = await fn(c);
    await c.commit();
    return out;
  } catch (e) {
    await c.rollback();
    throw e;
  } finally {
    c.release();
  }
}
