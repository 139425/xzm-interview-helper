// Intentionally empty by default.
// Add Drizzle tables here when the site actually needs a database.
// See examples/d1/db/schema.ts for an opt-in example.
import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core';
export const workspaces = sqliteTable('workspaces', {
  userId: text('user_id').primaryKey(), version: integer('version').notNull().default(1),
  state: text('state').notNull(), updatedAt: text('updated_at').notNull(),
});
export const marketCache = sqliteTable('market_cache', {
  key: text('key').primaryKey(), payload: text('payload').notNull(), updatedAt: text('updated_at').notNull(),
});
