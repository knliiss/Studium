#!/bin/bash
# Quick Reference: start-local script commands

# ============================================================================
# RECOMMENDED USAGE PATTERNS
# ============================================================================

# 📍 First time: Full stack setup
./infra/scripts/local/start-local.sh

# 📍 After code changes (auto-detects what changed)
./infra/scripts/local/start-local.sh

# 📍 Only frontend file changed (most frequent during UI work)
./infra/scripts/local/start-local.sh -f

# 📍 Something broke, force full rebuild
./infra/scripts/local/start-local.sh -r

# 📍 Just restart containers (no rebuild)
./infra/scripts/local/start-local.sh -s

# ============================================================================
# TROUBLESHOOTING
# ============================================================================

# Fix corrupted cache
rm .build-cache && ./infra/scripts/local/start-local.sh

# Full clean restart (like first time)
rm .build-cache
docker compose -f infra/docker/docker-compose.local.yml down -v
./infra/scripts/local/start-local.sh

# ============================================================================
# ADVANCED: WITH ENVIRONMENT VARIABLES
# ============================================================================

# Run with multiple auth-service replicas
AUTH_REPLICAS=3 ./infra/scripts/local/start-local.sh

# Mix: 2 auth replicas, rebuild everything
AUTH_REPLICAS=2 ./infra/scripts/local/start-local.sh -r

# Disable demo seed
DEMO_SEED_ENABLED=false ./infra/scripts/local/start-local.sh

# ============================================================================
# HELP
# ============================================================================

./infra/scripts/local/start-local.sh --help

