#!/usr/bin/env bash
# Instala el pre-commit que pasa gitleaks por lo que vas a commitear.
# Los hooks no viajan con el clon: ejecutar una vez por clon (bash tools/instalar-hooks.sh).
set -euo pipefail
raiz="$(git rev-parse --show-toplevel)"
cat > "$raiz/.git/hooks/pre-commit" <<'HOOK'
#!/usr/bin/env bash
# Repo PÚBLICO: nada con pinta de secreto entra en un commit.
if ! command -v gitleaks >/dev/null 2>&1; then
  echo "pre-commit: falta gitleaks (scoop install gitleaks). Commit bloqueado." >&2
  exit 1
fi
exec gitleaks git --pre-commit --staged --redact -v
HOOK
chmod +x "$raiz/.git/hooks/pre-commit"
echo "pre-commit con gitleaks instalado en $raiz"
