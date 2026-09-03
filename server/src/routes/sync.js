const express = require('express');
const pool = require('../db/pool');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireAuth, async (req, res) => {
  const [rows] = await pool.query(
    'SELECT events_json, categories_json, updated_at FROM user_data WHERE user_id = ?',
    [req.userId]
  );
  const row = rows[0];
  if (!row) return res.json({ events: [], categories: [], updatedAt: null });

  res.json({
    events: JSON.parse(row.events_json || '[]'),
    categories: JSON.parse(row.categories_json || '[]'),
    updatedAt: row.updated_at,
  });
});

router.put('/', requireAuth, async (req, res) => {
  const { events, categories } = req.body || {};
  if (!Array.isArray(events) || !Array.isArray(categories)) {
    return res.status(400).json({ error: 'invalid_body' });
  }

  await pool.query(
    `INSERT INTO user_data (user_id, events_json, categories_json)
     VALUES (?, ?, ?)
     ON DUPLICATE KEY UPDATE events_json = VALUES(events_json), categories_json = VALUES(categories_json)`,
    [req.userId, JSON.stringify(events), JSON.stringify(categories)]
  );

  res.json({ ok: true });
});

module.exports = router;
