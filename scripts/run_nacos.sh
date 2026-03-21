#!/bin/bash
set -euo pipefail
PROJECT_ROOT="/Users/mhhh/java_learn/InterviewWebsite"
NACOS_HOME="$PROJECT_ROOT/nacos/nacos"
JAVA_HOME="/Users/mhhh/.sdkman/candidates/java/8.0.292-zulu/zulu-8.jdk/Contents/Home"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

exec "$JAVA_HOME/bin/java" \
  -Xms512m -Xmx512m -Xmn256m \
  -Dnacos.standalone=true \
  -Dnacos.member.list= \
  -Dloader.path="$NACOS_HOME/plugins,$NACOS_HOME/plugins/health,$NACOS_HOME/plugins/cmdb,$NACOS_HOME/plugins/selector" \
  -Dnacos.home="$NACOS_HOME" \
  -Dnacos.core.auth.enabled=false \
  -jar "$NACOS_HOME/target/nacos-server.jar" \
  --spring.config.additional-location="file:$NACOS_HOME/conf/" \
  --logging.config="$NACOS_HOME/conf/nacos-logback.xml" \
  --server.max-http-header-size=524288
