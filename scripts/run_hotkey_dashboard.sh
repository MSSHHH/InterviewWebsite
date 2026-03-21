#!/bin/bash
set -euo pipefail
PROJECT_ROOT="/Users/mhhh/java_learn/InterviewWebsite"
JAVA_HOME="/Users/mhhh/.sdkman/candidates/java/8.0.292-zulu/zulu-8.jdk/Contents/Home"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

export MYSQL_HOST=127.0.0.1
export MYSQL_USER=root
export MYSQL_PASS=10011001
export etcdServer=http://127.0.0.1:2379

exec "$JAVA_HOME/bin/java" -jar "$PROJECT_ROOT/hotkey/hotkey-dashboard.jar" \
  --server.port=8081 \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/hotkey_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
