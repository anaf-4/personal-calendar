const express = require('express');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');
const pool = require('../db/pool');
const { requireAuth, issueToken } = require('../middleware/auth');

const router = express.Router();

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Discord requires the redirect_uri used in the token exchange to exactly match the one
// used to start the authorization request. Since this server is reachable at more than one
// address (LAN IP, public IP, ...), build it from whichever host the request actually came
// in on rather than a single fixed value — each address just needs to also be registered as
// a valid Redirect URI in the Discord app's OAuth2 settings.
function discordRedirectUri(req) {
  return `${req.protocol}://${req.get('host')}/api/auth/callback/discord`;
}

router.post('/register', async (req, res) => {
  const { email, password, displayName } = req.body || {};
  if (!email || !EMAIL_RE.test(email)) return res.status(400).json({ error: 'invalid_email' });
  if (!password || password.length < 8) return res.status(400).json({ error: 'weak_password' });

  const [existing] = await pool.query('SELECT id FROM users WHERE email = ?', [email]);
  if (existing.length > 0) return res.status(409).json({ error: 'email_taken' });

  const id = uuidv4();
  const passwordHash = await bcrypt.hash(password, 10);
  await pool.query(
    'INSERT INTO users (id, email, password_hash, display_name) VALUES (?, ?, ?, ?)',
    [id, email, passwordHash, displayName || null]
  );
  await pool.query(
    'INSERT INTO user_data (user_id, events_json, categories_json) VALUES (?, ?, ?)',
    [id, '[]', '[]']
  );

  res.json({ token: issueToken(id), user: { id, email, displayName: displayName || null } });
});

router.post('/login', async (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) return res.status(400).json({ error: 'missing_credentials' });

  const [rows] = await pool.query(
    'SELECT id, email, password_hash, display_name FROM users WHERE email = ?',
    [email]
  );
  const user = rows[0];
  if (!user || !user.password_hash) return res.status(401).json({ error: 'invalid_credentials' });

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) return res.status(401).json({ error: 'invalid_credentials' });

  res.json({
    token: issueToken(user.id),
    user: { id: user.id, email: user.email, displayName: user.display_name },
  });
});

router.get('/discord', (req, res) => {
  const params = new URLSearchParams({
    client_id: process.env.DISCORD_CLIENT_ID,
    redirect_uri: discordRedirectUri(req),
    response_type: 'code',
    scope: 'identify email',
  });
  res.redirect(`https://discord.com/api/oauth2/authorize?${params.toString()}`);
});

router.get('/callback/discord', async (req, res) => {
  const scheme = process.env.APP_CALLBACK_SCHEME || 'personalcalendar';
  const code = req.query.code;
  if (!code) return res.redirect(`${scheme}://auth-callback?error=missing_code`);

  try {
    const tokenResp = await fetch('https://discord.com/api/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: process.env.DISCORD_CLIENT_ID,
        client_secret: process.env.DISCORD_CLIENT_SECRET,
        grant_type: 'authorization_code',
        code,
        redirect_uri: discordRedirectUri(req),
      }),
    });
    if (!tokenResp.ok) throw new Error('token_exchange_failed');
    const tokenData = await tokenResp.json();

    const userResp = await fetch('https://discord.com/api/users/@me', {
      headers: { Authorization: `Bearer ${tokenData.access_token}` },
    });
    if (!userResp.ok) throw new Error('user_fetch_failed');
    const discordUser = await userResp.json();

    const [rows] = await pool.query('SELECT id FROM users WHERE discord_id = ?', [discordUser.id]);
    let userId = rows[0] && rows[0].id;

    if (!userId) {
      userId = require('uuid').v4();
      await pool.query(
        'INSERT INTO users (id, discord_id, discord_username, display_name, email) VALUES (?, ?, ?, ?, ?)',
        [userId, discordUser.id, discordUser.username, discordUser.global_name || discordUser.username, discordUser.email || null]
      );
      await pool.query(
        'INSERT INTO user_data (user_id, events_json, categories_json) VALUES (?, ?, ?)',
        [userId, '[]', '[]']
      );
    }

    const jwtToken = issueToken(userId);
    res.redirect(`${scheme}://auth-callback?token=${encodeURIComponent(jwtToken)}`);
  } catch (err) {
    res.redirect(`${scheme}://auth-callback?error=discord_auth_failed`);
  }
});

router.get('/me', requireAuth, async (req, res) => {
  const [rows] = await pool.query(
    'SELECT id, email, discord_username, display_name FROM users WHERE id = ?',
    [req.userId]
  );
  const row = rows[0];
  if (!row) return res.status(404).json({ error: 'not_found' });
  res.json({
    user: {
      id: row.id,
      email: row.email,
      displayName: row.display_name,
      discordUsername: row.discord_username,
    },
  });
});

module.exports = router;
