import { writeFile } from 'node:fs/promises';
const raw = process.env.API_BASE_URL;
if (!raw) throw new Error('Set the GitHub Actions repository variable API_BASE_URL to your HTTPS backend tunnel URL.');
const url = new URL(raw);
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('API_BASE_URL must be an HTTPS origin, without credentials, path, query or fragment.');
const config = { apiBaseUrl: url.origin, ngrokSkipBrowserWarning: /(^|\.)ngrok(-free)?\.(app|dev|io)$/.test(url.hostname) };
await writeFile(new URL('../frontend/public/runtime-config.json', import.meta.url), JSON.stringify(config,null,2)+'\n');
