// Idempotent schema, applied once per server process (ensureSchema). Additive changes only:
// add a new statement at the end rather than editing one that has already run.
export const SCHEMA: string[] = [
  `CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NULL,
    phone VARCHAR(40) NULL,
    token CHAR(40) NULL UNIQUE,
    color CHAR(7) NOT NULL,
    default_currency CHAR(3) NOT NULL DEFAULT 'ILS',
    created_by INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  `CREATE TABLE IF NOT EXISTS friendships (
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id)
  )`,
  `CREATE TABLE IF NOT EXISTS \`groups\` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type ENUM('home','trip','couple','other') NOT NULL DEFAULT 'other',
    default_currency CHAR(3) NOT NULL DEFAULT 'ILS',
    simplify_debts TINYINT(1) NOT NULL DEFAULT 1,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    created_by INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  `CREATE TABLE IF NOT EXISTS group_members (
    group_id INT NOT NULL,
    user_id INT NOT NULL,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    KEY (user_id)
  )`,
  `CREATE TABLE IF NOT EXISTS expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NULL,
    description VARCHAR(255) NOT NULL,
    cost BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    date DATE NOT NULL,
    category VARCHAR(40) NOT NULL DEFAULT 'general',
    notes TEXT NULL,
    is_payment TINYINT(1) NOT NULL DEFAULT 0,
    split_type ENUM('equal','exact','percent','shares','adjustment') NOT NULL DEFAULT 'equal',
    \`repeat\` ENUM('none','weekly','biweekly','monthly','yearly') NOT NULL DEFAULT 'none',
    repeat_spawned TINYINT(1) NOT NULL DEFAULT 0,
    receipt VARCHAR(120) NULL,
    source VARCHAR(40) NULL,
    source_key VARCHAR(190) NULL,
    created_by INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INT NULL,
    updated_at DATETIME NULL,
    deleted_by INT NULL,
    deleted_at DATETIME NULL,
    KEY (group_id, date),
    KEY (date),
    UNIQUE KEY (source, source_key)
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  `CREATE TABLE IF NOT EXISTS expense_shares (
    expense_id INT NOT NULL,
    user_id INT NOT NULL,
    paid BIGINT NOT NULL DEFAULT 0,
    owed BIGINT NOT NULL DEFAULT 0,
    input DOUBLE NULL,
    PRIMARY KEY (expense_id, user_id),
    KEY (user_id)
  )`,
  `CREATE TABLE IF NOT EXISTS comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    expense_id INT NOT NULL,
    user_id INT NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY (expense_id)
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  `CREATE TABLE IF NOT EXISTS activity (
    id INT AUTO_INCREMENT PRIMARY KEY,
    actor_id INT NOT NULL,
    type VARCHAR(40) NOT NULL,
    group_id INT NULL,
    expense_id INT NULL,
    text VARCHAR(500) NOT NULL,
    effects JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY (group_id),
    KEY (expense_id)
  ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  `CREATE TABLE IF NOT EXISTS activity_users (
    activity_id INT NOT NULL,
    user_id INT NOT NULL,
    PRIMARY KEY (user_id, activity_id)
  )`,
];
