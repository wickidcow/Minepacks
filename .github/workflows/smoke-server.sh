#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <server.jar> <minepacks.jar> <workdir> <expected-log-line>" >&2
  exit 2
fi

SERVER_JAR="$1"
MINEPACKS_JAR="$2"
WORKDIR="$3"
EXPECTED_LOG="$4"
LOG="$WORKDIR/server.log"
PID=''
INPUT_OPEN=0

cleanup() {
  set +e
  if [ "$INPUT_OPEN" -eq 1 ]; then
    printf 'stop\n' >&3 2>/dev/null
  fi
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    for _ in $(seq 1 20); do
      kill -0 "$PID" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$PID" 2>/dev/null; then
      kill "$PID" 2>/dev/null
    fi
  fi
  if [ "$INPUT_OPEN" -eq 1 ]; then
    exec 3>&-
  fi
}
trap cleanup EXIT

print_diagnostic() {
  local headline="$1"
  local reason
  reason="$(grep -E -i 'Minepacks|ERROR|Exception|Caused by|Could not load|failed|unsupported' "$LOG" 2>/dev/null | tail -n 1 || true)"
  if [ -z "$reason" ]; then reason="$headline"; fi
  reason="${reason//'%'/'%25'}"
  reason="${reason//$'\r'/'%0D'}"
  reason="${reason//$'\n'/'%0A'}"
  echo "::error title=Minepacks server smoke::${reason}" >&2
  echo "$headline" >&2
  echo '----- relevant server log -----' >&2
  grep -E -i 'Minepacks|ERROR|WARN|Exception|Caused by|Could not load|failed|unsupported|Done \(' "$LOG" 2>/dev/null | tail -n 100 >&2 || true
  echo '----- server log tail -----' >&2
  tail -n 60 "$LOG" >&2 || true
}

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR/plugins"
cp "$SERVER_JAR" "$WORKDIR/server.jar"
cp "$MINEPACKS_JAR" "$WORKDIR/plugins/Minepacks.jar"

cat > "$WORKDIR/eula.txt" <<'EOF'
eula=true
EOF

cat > "$WORKDIR/server.properties" <<'EOF'
online-mode=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=1
generate-structures=false
level-type=minecraft:flat
motd=Minepacks CI smoke test
EOF

mkfifo "$WORKDIR/server.stdin"
(
  cd "$WORKDIR"
  exec java -Xms512M -Xmx1024M -jar server.jar --nogui < server.stdin > server.log 2>&1
) &
PID=$!

# The server process opens the FIFO for reading while this side opens it for writing.
exec 3>"$WORKDIR/server.stdin"
INPUT_OPEN=1

READY=0
for _ in $(seq 1 180); do
  if grep -Eq 'Done \([0-9.]+s\)!' "$LOG" 2>/dev/null; then
    READY=1
    break
  fi

  if ! kill -0 "$PID" 2>/dev/null; then
    echo 'Server process exited before startup completed.' >&2
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  print_diagnostic 'Server did not reach the ready state.'
  exit 1
fi

if ! grep -Fq "$EXPECTED_LOG" "$LOG"; then
  print_diagnostic "Minepacks did not emit the expected compatibility message: $EXPECTED_LOG"
  exit 1
fi

if grep -Eq 'Error occurred while enabling Minepacks|Could not load .*Minepacks|Minepacks refused to initialize backpack storage' "$LOG"; then
  print_diagnostic 'Minepacks reported a startup failure.'
  exit 1
fi

echo "Minepacks enabled successfully: $EXPECTED_LOG"
printf 'stop\n' >&3
exec 3>&-
INPUT_OPEN=0

STOPPED=0
for _ in $(seq 1 60); do
  if ! kill -0 "$PID" 2>/dev/null; then
    STOPPED=1
    break
  fi
  sleep 1
done

if [ "$STOPPED" -ne 1 ]; then
  print_diagnostic 'Server did not stop cleanly after the stop command.'
  exit 1
fi

wait "$PID"
PID=''

echo 'Server startup and graceful shutdown smoke test passed.'
