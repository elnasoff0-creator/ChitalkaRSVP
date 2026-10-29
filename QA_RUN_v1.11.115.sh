#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
passed=0
failed=0
: > QA_RESULTS_v1.11.115.txt
for script in qa/*.sh qa/*.py; do
  printf 'RUN %-42s ' "$script" | tee -a QA_RESULTS_v1.11.115.txt
  if [[ "$script" == *.py ]]; then python "$script" > /tmp/v11115_qa.out 2>&1; else bash "$script" > /tmp/v11115_qa.out 2>&1; fi
  status=$?
  if [ "$status" -eq 0 ]; then echo PASS | tee -a QA_RESULTS_v1.11.115.txt; passed=$((passed+1)); else
    echo FAIL | tee -a QA_RESULTS_v1.11.115.txt; tail -12 /tmp/v11115_qa.out | tee -a QA_RESULTS_v1.11.115.txt; failed=$((failed+1))
  fi
done
printf 'TOTAL scripts=%d PASS=%d FAIL=%d\n' "$((passed+failed))" "$passed" "$failed" | tee -a QA_RESULTS_v1.11.115.txt
printf 'Scope: source syntax + structural checks + desktop JVM unit suites; NO Android Gradle build/device test.\n' | tee -a QA_RESULTS_v1.11.115.txt
exit "$((failed>0))"
