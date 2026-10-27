/**
 * One-off local screenshot capture for portfolio README.
 * Requires Vite + backend running. Secrets never written to disk images
 * (no reveal dialogs). Credentials via env only.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.resolve(__dirname, '../frontend/package.json'));
const { chromium } = require('@playwright/test');
const outDir = path.resolve(__dirname, '../docs/images');
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173';
const email = process.env.PAYFLOW_DEMO_EMAIL;
const password = process.env.PAYFLOW_DEMO_PASSWORD;
const paymentId = process.env.PAYFLOW_DEMO_PAYMENT_ID;

if (!email || !password || !paymentId) {
  console.error('Set PAYFLOW_DEMO_EMAIL, PAYFLOW_DEMO_PASSWORD, PAYFLOW_DEMO_PAYMENT_ID');
  process.exit(1);
}

fs.mkdirSync(outDir, { recursive: true });

const shots = [];

async function shot(page, name, fullPage = false) {
  const file = path.join(outDir, name);
  await page.waitForTimeout(400);
  await page.screenshot({ path: file, fullPage, type: 'png' });
  shots.push(name);
  console.log('captured', name);
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 1,
});
const page = await context.newPage();

await page.goto(`${baseURL}/login`);
await page.getByLabel(/^email$/i).fill(email);
await page.getByLabel(/^password$/i).fill(password);
await page.getByRole('button', { name: /^sign in$/i }).click();
await page.waitForURL(/\/dashboard/);
await page.getByRole('heading', { name: /^overview$/i }).waitFor();

// Prefer merchant with activity
const selector = page.getByRole('button', { name: /merchant selector/i });
await selector.click();
const northwind = page.getByRole('option', { name: /northwind sandbox merchants/i });
if (await northwind.count()) {
  await northwind.click();
} else {
  await page.keyboard.press('Escape');
}
await page.getByRole('heading', { name: /northwind|overview/i }).first().waitFor({ timeout: 10000 }).catch(() => {});
await page.waitForTimeout(800);
await shot(page, '01-merchant-analytics-dashboard.png');

// Merchant selector open (list + switcher)
await selector.click();
await page.getByRole('listbox', { name: /owned merchants/i }).waitFor();
await shot(page, '02-merchant-list-and-selector.png');
await page.keyboard.press('Escape');

await page.getByRole('link', { name: /^merchants$/i }).click();
await page.getByRole('heading', { name: /^merchants$/i }).waitFor();
await page.waitForTimeout(500);
await shot(page, '02b-merchants-page.png');

await page.getByRole('link', { name: /^payments$/i }).click();
await page.getByRole('heading', { name: /^payments$/i }).waitFor();
await page.waitForTimeout(600);
await shot(page, '03-payment-list.png');

await page.goto(`${baseURL}/payments/${paymentId}`);
await page.getByText(/CAPTURED|captured/i).first().waitFor({ timeout: 15000 });
await page.waitForTimeout(600);
await shot(page, '04-captured-payment-detail.png', true);

// Refunds: payment detail usually shows history; also visit refunds nav if present
const refundsNav = page.getByRole('link', { name: /^refunds$/i });
if (await refundsNav.count()) {
  await refundsNav.click();
  await page.waitForTimeout(800);
  await shot(page, '05-partial-refund-history.png');
} else {
  await shot(page, '05-partial-refund-history.png', true);
}

await page.getByRole('link', { name: /api keys/i }).click();
await page.getByRole('heading', { name: /api keys/i }).waitFor();
await page.waitForTimeout(600);
// Ensure no secret dialog — Escape just in case
await page.keyboard.press('Escape');
await shot(page, '06-api-key-management.png');

await page.getByRole('link', { name: /^webhooks$/i }).click();
await page.getByRole('heading', { name: /webhooks/i }).waitFor();
await page.waitForTimeout(600);
await page.keyboard.press('Escape');
await shot(page, '07-webhook-configuration.png');

await browser.close();
console.log(JSON.stringify({ ok: true, count: shots.length, shots }));
