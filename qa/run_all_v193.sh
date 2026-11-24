#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
commands=(
'qa/check_java_syntax.sh'
'qa/test_web_transfer_192.sh'
'qa/typecheck_web_transfer_server_192.sh'
'qa/typecheck_web_transfer_history_193.sh'
'qa/typecheck_web_transfer_activity_193.sh'
'qa/check_web_transfer_ui_193.py'
'qa/test_tts_dictionary_csv_190.sh'
'qa/typecheck_dictionary_store_190.sh'
'qa/test_tts_scroll_controller_190.sh'
'qa/test_reader_and_comic_restore_191.sh'
'qa/check_v190_contracts.py'
'qa/test_reader_tts_scroll_189.sh'
'qa/test_edge_startup_188.sh'
'qa/check_v188_contracts.py'
'qa/test_edge_sentence_blocks_182.sh'
'qa/test_edge_sentence_seek.sh'
'qa/check_v182_contracts.py'
'qa/test_edge_live_buffer.sh'
'qa/test_edge_transitions_183.sh'
'qa/test_edge_prefetch_planner.sh'
'qa/check_edge_serial_playback.sh'
'qa/test_edge_startup_singleflight.sh'
'qa/test_edge_priority.sh'
'qa/test_edge_429_silent.sh'
'qa/test_edge_reuse_pool.sh'
'qa/test_edge_idle_expiry_compilation.py'
'qa/test_pet_quotes_184.sh'
'qa/test_quotes_sqlite_184.py'
'qa/test_reader_selection_navigation_185.sh'
'qa/test_word_boundary_refresh_186.sh'
'qa/test_reader_touch_policy_186.sh'
'qa/check_v186_contracts.py'
)
count=0
for path in "${commands[@]}";do
  count=$((count+1))
  echo "==== $count/${#commands[@]} $path ===="
  if [[ "$path" == *.py ]];then python3 "$path";else bash "$path";fi
done
mkdir -p /tmp/reader-qa-193
javac -d /tmp/reader-qa-193 qa/ReaderActivityMethodCheck.java
java -cp /tmp/reader-qa-193 ReaderActivityMethodCheck
echo "ALL ${#commands[@]}+1 CHECKS PASSED"
