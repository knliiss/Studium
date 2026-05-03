# Smart Start-Local Script Guide

## Overview

The updated `start-local.sh` (Unix/macOS) and `start-local.bat` (Windows) scripts now feature **intelligent change detection** and **incremental rebuilding**.

## Key Features

### 🔍 Smart Change Detection
- **Automatic Detection**: Monitors source code changes and rebuilds only affected services
- **Fast Startup**: Subsequent runs without code changes skip unnecessary rebuilds
- **Build Cache**: Stores file hashes in `.build-cache` for quick comparison

### 🚀 Incremental Container Restart
- **Targeted Restart**: Only restarts Docker containers for services with code changes
- **Bandwidth Efficient**: Skips rebuilding Docker images for unchanged services
- **Infrastructure Preservation**: Keeps postgres, redis, kafka, minio running

### 🔄 Rebuild Modes

| Mode | Command | Behavior |
|------|---------|----------|
| **Smart** | `./start-local.sh` | Detect changes, rebuild/restart only affected services |
| **Force Rebuild** | `./start-local.sh -r` or `--rebuild` | Force full rebuild of ALL services (ignores cache) |
| **Skip Build** | `./start-local.sh -s` or `--skip-build` | Use existing JARs/containers, only restart stack |
| **Frontend Only** | `./start-local.sh -f` or `--frontend-only` | Quick frontend rebuild without backend restart |

## Usage Examples

### First Time Setup
```bash
./infra/scripts/local/start-local.sh
# Builds all services, starts full stack
# Creates .build-cache file with hashes
```

### Subsequent Run (No Code Changes)
```bash
./infra/scripts/local/start-local.sh
# Detects no changes
# Only ensures containers are running
# Takes ~5-10 seconds
```

### After Editing auth-service Code
```bash
./infra/scripts/local/start-local.sh
# Detects change in auth-service only
# Rebuilds auth-service JAR only
# Restarts only auth-service container
# Backend services remain running
```

### Force Full Rebuild (Troubleshooting)
```bash
./infra/scripts/local/start-local.sh -r
# Or: ./start-local.sh --rebuild
# Ignores cache, rebuilds ALL services
# Full container restart
```

### Skip Build (Quick Restart)
```bash
./infra/scripts/local/start-local.sh -s
# Or: ./start-local.sh --skip-build
# Uses existing JARs/containers
# Only restarts docker-compose stack
```

### Frontend-Only Changes
```bash
./infra/scripts/local/start-local.sh -f
# Or: ./start-local.sh --frontend-only
# Rebuilds only frontend Docker image
# Other services untouched
# Fastest possible refresh (~30 seconds)
```

## Build Cache

The `.build-cache` file stores MD5 hashes of source files:

```
auth-service:hash1:hash2
profile-service:hash3:hash4
frontend:hash5:hash6
```

**Auto-managed**: You don't need to edit this file
**Auto-cleared**: Can safely delete if cache becomes inconsistent

## Monitored File Types

### Backend Services
- `apps/{service}/src/**/*.java`
- `apps/{service}/src/**/*.kt`
- `apps/{service}/build.gradle`

### Frontend
- `apps/frontend/src/**/*.ts`
- `apps/frontend/src/**/*.tsx`
- `apps/frontend/src/**/*.css`
- `apps/frontend/vite.config.ts`
- `apps/frontend/package.json`
- `apps/frontend/tsconfig.json`

## Platform-Specific Notes

### macOS / Linux
- Uses `md5sum` for hashing (standard Unix tool)
- Requires `find` command
- Recommended: Install with Homebrew or system package manager

### Windows
- Windows batch version (`.bat`) has fallback behavior
- Full rebuild is performed in force mode `-r`
- Smart change detection can be enhanced with PowerShell in future releases
- Note: Requires Docker Desktop for Windows

## Troubleshooting

### Cache Out of Sync
```bash
rm .build-cache
./start-local.sh     # Rebuilds all, regenerates cache
```

### Specific Service Not Restarting
```bash
./start-local.sh -r   # Force full rebuild
```

### Container Connection Issues
```bash
./start-local.sh -s   # Skip build, just restart containers
```

## Performance Comparison

| Scenario | Before (All Rebuild) | After (Smart) |
|----------|---------------------|--------------|
| Edit 1 file, no deps | ~5-10 min | ~30-60 sec |
| No changes | ~5-10 min | ~5-10 sec |
| First run | ~5-10 min | ~5-10 min |
| Full rebuild `-r` | N/A | ~5-10 min |

## Notes

- **Demo Seed**: Runs automatically unless `DEMO_SEED_ENABLED=false`
- **Replica Scaling**: Use `AUTH_REPLICAS=2 ./start-local.sh` to scale services
- **Port Conflicts**: Configure ports in `.env` (default: 8080-8090 for backend, 3000 for frontend)

---

For issues or questions, see `./infra/scripts/local/start-local.sh --help`

