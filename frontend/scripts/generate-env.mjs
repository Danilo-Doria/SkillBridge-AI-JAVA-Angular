import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });

let apiUrl = process.env.API_URL || '/api';
if (!apiUrl.startsWith('/')) {
  console.warn(`API_URL="${apiUrl}" es absoluta; se ignora y se usa /api (la cookie HttpOnly requiere mismo origen).`);
  apiUrl = '/api';
}

writeFileSync('public/env.js', `window.__env = { API_URL: ${JSON.stringify(apiUrl)} };\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl}`);