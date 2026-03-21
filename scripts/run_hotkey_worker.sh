#!/bin/bash
set -euo pipefail
PROJECT_ROOT="/Users/mhhh/java_learn/InterviewWebsite"
JAVA_HOME="/Users/mhhh/.sdkman/candidates/java/8.0.292-zulu/zulu-8.jdk/Contents/Home"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

exec "$JAVA_HOME/bin/java" -jar "$PROJECT_ROOT/hotkey/hotkey-worker.jar" \
  --etcdServer=http://127.0.0.1:2379 \
  --localAddress=127.0.0.1 \
  --workerPath=default \
  --nettyPort=9527 \
  --server.port=18080
