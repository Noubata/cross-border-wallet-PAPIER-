# CrossBorder Wallet Frontend

Standalone React, TypeScript, and Vite client for the CrossBorder Wallet backend.

## Run

Start the backend first. Then, from this directory:

```bash
npm install
npm run dev
```

Vite serves the frontend at `http://localhost:5173` and proxies `/api` to `http://localhost:8080` to avoid browser CORS restrictions during local development. Set `VITE_PROXY_TARGET` to proxy to another local backend. For deployment, set `VITE_API_BASE_URL` to the API origin or serve the app and API behind a same-origin reverse proxy. The backend currently does not configure cross-origin access.

## Verify

```bash
npm run build
npm run lint
```

The UI uses the implemented backend routes only. Recipient lookup, transaction-history listing, KYC updates, and payment-status polling are not exposed by the backend and are not simulated. Funding remains pending until the backend accepts a valid signed gateway webhook.