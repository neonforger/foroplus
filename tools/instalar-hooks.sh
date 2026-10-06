#!/usr/bin/env bash
# Instala el pre-commit que pasa gitleaks por lo que vas a commitear.
# Los hooks no viajan con el clon: ejecutar una vez por clon (bash tools/instalar-hooks.sh).
set -euo pipefail
raiz="$(git rev-parse --show-toplevel)"
if [ -e "$raiz/.git/hooks/pre-commit" ]; then
  cp "$raiz/.git/hooks/pre-commit" "$raiz/.git/hooks/pre-commit.anterior"
  echo "había un pre-commit: guardado como pre-commit.anterior"
fi
cat > "$raiz/.git/hooks/pre-commit" <<'HOOK'
#!/usr/bin/env bash
# Repo PÚBLICO: el autor de cada commit lo ve cualquiera. Solo la dirección noreply de GitHub.
case "$(git config user.email)" in
  *@users.noreply.github.com) ;;
  *) echo "pre-commit: user.email no es noreply de GitHub; configúralo en este clon:" >&2
     echo "  git config user.email 112471184+albertdom@users.noreply.github.com" >&2
     exit 1 ;;
esac
# Y nada con pinta de secreto entra en un commit.
if ! command -v gitleaks >/dev/null 2>&1; then
  echo "pre-commit: falta gitleaks (scoop install gitleaks). Commit bloqueado." >&2
  exit 1
fi
exec gitleaks git --pre-commit --staged --redact -v
HOOK
chmod +x "$raiz/.git/hooks/pre-commit"
echo "pre-commit con gitleaks instalado en $raiz"
