# frontend

Next.js 16 (App Router) + TypeScript + Tailwind. Logowanie 1 kliknięciem (role switcher w nagłówku).

```bash
npm install
BACKEND_CORE_URL=http://localhost:8080 AI_GATEWAY_URL=http://localhost:8000 npm run dev   # http://localhost:3000
npm run build && npm run typecheck
```

Rewrites (`next.config.mjs`) są wyliczane w czasie `next build` — w Dockerze adresy usług przekazywane są jako build args.
