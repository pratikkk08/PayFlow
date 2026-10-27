# Portfolio screenshots

Checked-in PNGs from a local sandbox run (2026-10-27). Synthetic demo operator only. Linked from the root [README.md](../../README.md).

## Files

| File | Subject |
|------|---------|
| `01-merchant-analytics-dashboard.png` | Overview metrics + sandbox disclaimers |
| `02-merchant-list-and-selector.png` | Merchant selector dropdown (multi-merchant) |
| `02b-merchants-page.png` | Merchants management list |
| `03-payment-list.png` | Simulated payment list |
| `04-captured-payment-detail.png` | CAPTURED payment + partial refund history |
| `05-partial-refund-history.png` | Refunds nav (CAPTURED payments → manage refunds) |
| `06-api-key-management.png` | TEST API keys metadata (prefix only; no reveal dialog) |
| `07-webhook-configuration.png` | Webhook endpoint config (secret prefix only) |

## Guidelines

- Capture from local Vite + Spring Boot only (`PAYMENT_PROCESSOR_MODE=sandbox`).
- Desktop viewport used: **1440×900**.
- Do **not** open secret-reveal dialogs when capturing.
- Do not commit `.env`, PEMs, or database dumps.
- Regenerate (optional): with backend/Vite up, set `PAYFLOW_DEMO_EMAIL`, `PAYFLOW_DEMO_PASSWORD`, `PAYFLOW_DEMO_PAYMENT_ID`, then run `node scripts/capture-portfolio-screenshots.mjs` from the repo root (uses `frontend` Playwright).
