#!/usr/bin/env bash
# Runs a CI command and, when it fails, puts the important part of its output into an
# error annotation, which stays readable through the API after the run.
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  {
    grep -E '^e: |error:|Error:|FAIL|What went wrong|REJECTED|Exception' "$log" | head -40
    echo "---- last lines ----"
    tail -n 40 "$log"
  } | python3 -c 'import sys; d=sys.stdin.read()[-60000:]; print("::error title=" + sys.argv[1] + "::" + d.replace("%","%25").replace("\r","%0D").replace("\n","%0A"))' "$1 failed ($rc)"
fi
exit "$rc"
