# Studium Frontend

Frontend implementation lives in this directory and follows:

- `DESIGN.md`
- `ARCHITECTURE.md`
- `FRONTEND_IMPLEMENTATION.md`
- `docs/API_CONTRACT.md`

Local development:

```bash
./infra/scripts/local/start-local.sh

cd apps/frontend
cp .env.example .env
npm install
npm run dev
```

Frontend-only Docker rebuild:

```bash
./infra/scripts/local/start-local.sh --frontend-only
```

Host-side local frontend development:

```bash
cd apps/frontend
cp .env.example .env
npm install
npm run dev
```

Production build:

```bash
npm run build
```

Type and lint verification:

```bash
npm run typecheck
npm run lint
```
