#!/bin/bash
set -euo pipefail
PROJECT_ROOT="/Users/mhhh/java_learn/InterviewWebsite"
JAVA_HOME="/Users/mhhh/.sdkman/candidates/java/8.0.292-zulu/zulu-8.jdk/Contents/Home"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

exec "$JAVA_HOME/bin/java" -jar "$PROJECT_ROOT/sentinel/sentinel-dashboard-1.8.8.jar" --server.port=8858
