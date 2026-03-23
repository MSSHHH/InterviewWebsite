#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/mianshiya-next-backend"
FRONTEND_DIR="$PROJECT_ROOT/mianshiya-next-frontend"
LOG_DIR="$PROJECT_ROOT/logs/dev"

mkdir -p "$LOG_DIR"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $*"; }
ok() { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err() { echo -e "${RED}[ERR]${NC} $*"; }

is_listening() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

is_tcp_open() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z "$host" "$port" >/dev/null 2>&1
    return $?
  fi
  (echo >"/dev/tcp/$host/$port") >/dev/null 2>&1
}

mysql_login_ok() {
  local host="$1"
  local port="$2"
  local user="$3"
  local password="$4"
  local mysql_cmd="${MYSQL_CMD:-mysql}"
  if [ -x "/usr/local/mysql/bin/mysql" ]; then
    mysql_cmd="/usr/local/mysql/bin/mysql"
  fi
  if [ -n "$password" ]; then
    "$mysql_cmd" --protocol=TCP -h"$host" -P"$port" -u"$user" -p"$password" -e "SELECT 1;" >/dev/null 2>&1
  else
    "$mysql_cmd" --protocol=TCP -h"$host" -P"$port" -u"$user" -e "SELECT 1;" >/dev/null 2>&1
  fi
}

start_oracle_mysql() {
  info "启动 Oracle MySQL..."
  launchctl start com.oracle.oss.mysql.mysqld >/dev/null 2>&1 || true
  sleep 2
}

wait_for_tcp_port() {
  local name="$1"
  local host="$2"
  local port="$3"
  local timeout_sec="$4"
  local i=0
  while [ "$i" -lt "$timeout_sec" ]; do
    if is_tcp_open "$host" "$port"; then
      ok "$name 已就绪（${host}:${port}）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  err "$name 启动超时（${host}:${port}）"
  return 1
}

wait_for_mysql_login() {
  local host="$1"
  local port="$2"
  local user="$3"
  local password="$4"
  local timeout_sec="$5"
  local i=0
  while [ "$i" -lt "$timeout_sec" ]; do
    if mysql_login_ok "$host" "$port" "$user" "$password"; then
      ok "MySQL 已接受登录（${user}@${host}:${port}）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  err "MySQL 登录超时（用户 ${user}@${host}:${port}）"
  return 1
}

ensure_mysql() {
  local mysql_host="${MYSQL_HOST:-127.0.0.1}"
  local mysql_port="${MYSQL_PORT:-3306}"
  local user="${MYSQL_USERNAME:-root}"
  local password="${MYSQL_PASSWORD:-10011001}"
  if is_tcp_open "$mysql_host" "$mysql_port"; then
    ok "MySQL 已运行（${mysql_host}:${mysql_port}）"
    wait_for_mysql_login "$mysql_host" "$mysql_port" "$user" "$password" 20 || warn "MySQL 端口已监听，但当前账号密码暂不可用"
    return
  fi
  start_oracle_mysql
  if wait_for_tcp_port "Oracle MySQL" "$mysql_host" "$mysql_port" 30; then
    ok "Oracle MySQL 已启动"
    wait_for_mysql_login "$mysql_host" "$mysql_port" "$user" "$password" 30 || warn "Oracle MySQL 已监听 ${mysql_port}，但当前账号密码暂不可用"
  else
    warn "Oracle MySQL 仍未监听 ${mysql_port}，请检查 /Library/LaunchDaemons/com.oracle.oss.mysql.mysqld.plist 和 /usr/local/mysql/data/mysqld.local.err"
  fi
}

wait_for_port() {
  local name="$1"
  local port="$2"
  local timeout_sec="$3"
  local i=0
  while [ "$i" -lt "$timeout_sec" ]; do
    if is_listening "$port"; then
      ok "$name 已就绪（端口 ${port}）"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  err "$name 启动超时（端口 ${port}）"
  return 1
}

start_brew_service() {
  local svc="$1"
  if command -v brew >/dev/null 2>&1; then
    brew services start "$svc" >/dev/null 2>&1 || return 1
    return 0
  fi
  return 1
}

ensure_redis() {
  if is_listening 6379; then
    ok "Redis 已运行"
    return
  fi
  info "启动 Redis..."
  start_brew_service redis || warn "无法自动启动 Redis，请手动检查"
  sleep 2
  is_listening 6379 && ok "Redis 已启动" || warn "Redis 仍未监听 6379"
}

ensure_etcd() {
  if is_listening 2379; then
    ok "etcd 已运行"
    return
  fi
  info "启动 etcd..."
  if command -v etcd >/dev/null 2>&1; then
    nohup etcd >"$LOG_DIR/etcd.log" 2>&1 &
  elif [ -x "/opt/homebrew/opt/etcd/bin/etcd" ]; then
    nohup /opt/homebrew/opt/etcd/bin/etcd >"$LOG_DIR/etcd.log" 2>&1 &
  else
    warn "未找到 etcd 可执行文件，请先安装 etcd"
    return
  fi
  wait_for_port "etcd" 2379 20 || true
}

ensure_elasticsearch() {
  if is_listening 9200; then
    ok "Elasticsearch 已运行"
    return
  fi
  info "启动 Elasticsearch..."
  start_brew_service elasticsearch-full || start_brew_service elasticsearch || warn "无法自动启动 Elasticsearch，请手动检查"
  wait_for_port "Elasticsearch" 9200 30 || true
}

ensure_kibana() {
  if is_listening 5601; then
    ok "Kibana 已运行"
    return
  fi
  info "启动 Kibana..."
  start_brew_service kibana-full || start_brew_service kibana || warn "无法自动启动 Kibana，请手动检查"
  wait_for_port "Kibana" 5601 30 || true
}

ensure_nacos() {
  if is_listening 8848; then
    ok "Nacos 已运行"
    return
  fi
  info "启动 Nacos..."
  nohup "$PROJECT_ROOT/scripts/run_nacos.sh" >"$LOG_DIR/nacos.log" 2>&1 &
  wait_for_port "Nacos" 8848 60 || true
}

ensure_sentinel() {
  if is_listening 8858; then
    ok "Sentinel Dashboard 已运行"
    return
  fi
  info "启动 Sentinel Dashboard..."
  nohup "$PROJECT_ROOT/scripts/run_sentinel.sh" >"$LOG_DIR/sentinel.log" 2>&1 &
  wait_for_port "Sentinel Dashboard" 8858 30 || true
}

ensure_hotkey_worker() {
  if is_listening 9527; then
    ok "HotKey Worker 已运行"
    return
  fi
  info "启动 HotKey Worker..."
  nohup "$PROJECT_ROOT/scripts/run_hotkey_worker.sh" >"$LOG_DIR/hotkey-worker.log" 2>&1 &
  wait_for_port "HotKey Worker" 9527 30 || true
}

ensure_hotkey_dashboard() {
  if is_listening 8081; then
    ok "HotKey Dashboard 已运行"
    return
  fi
  info "启动 HotKey Dashboard..."
  nohup "$PROJECT_ROOT/scripts/run_hotkey_dashboard.sh" >"$LOG_DIR/hotkey-dashboard.log" 2>&1 &
  wait_for_port "HotKey Dashboard" 8081 30 || true
}

start_backend_dev() {
  if is_listening 8101; then
    ok "后端已运行（8101）"
    return
  fi
  local api_key="${DASHSCOPE_API_KEY:-${AI_API_KEY:-}}"
  local mysql_user="${MYSQL_USERNAME:-root}"
  local mysql_password="${MYSQL_PASSWORD:-10011001}"
  local mysql_url="${MYSQL_URL:-jdbc:mysql://127.0.0.1:3306/mianshiya}"
  local spring_application_json
  if [ -z "$api_key" ]; then
    warn "DASHSCOPE_API_KEY / AI_API_KEY 未设置，使用占位值启动（AI 接口可能不可用）"
    api_key="dummy_for_dev_boot"
  fi
  info "使用 MySQL 凭据：${mysql_user}/******"
  spring_application_json=$(printf '{"spring":{"datasource":{"url":"%s","username":"%s","password":"%s"}}}' "$mysql_url" "$mysql_user" "$mysql_password")
  info "启动后端（热部署模式，Spring DevTools）..."
  nohup bash -lc "cd \"$BACKEND_DIR\" && DASHSCOPE_API_KEY=\"$api_key\" SPRING_APPLICATION_JSON='$spring_application_json' mvn -DskipTests spring-boot:run" >"$LOG_DIR/backend.log" 2>&1 &
  if ! wait_for_port "后端" 8101 120; then
    warn "后端日志末尾如下："
    tail -n 40 "$LOG_DIR/backend.log" || true
  fi
}

start_frontend_dev() {
  if is_listening 3001; then
    ok "前端已运行（3001）"
    return
  fi
  info "启动前端（热部署模式，Next HMR）..."
  nohup bash -lc "cd \"$FRONTEND_DIR\" && npm run dev -- -p 3001" >"$LOG_DIR/frontend.log" 2>&1 &
  wait_for_port "前端" 3001 120 || true
}

echo "=========================================="
echo "      面试鸭开发环境一键启动（热部署）"
echo "=========================================="

ensure_mysql
ensure_redis
ensure_etcd
ensure_elasticsearch
ensure_kibana
ensure_nacos
ensure_sentinel
ensure_hotkey_worker
ensure_hotkey_dashboard

start_backend_dev
start_frontend_dev

echo
echo "------------------------------------------"
echo "访问地址"
echo "- 前端:       http://localhost:3001"
echo "- 后端:       http://localhost:8101/api"
echo "- Nacos:      http://localhost:8848/nacos"
echo "- Sentinel:   http://localhost:8858"
echo "- HotKey:     http://localhost:8081"
echo "- Kibana:     http://localhost:5601"
echo "日志目录: $LOG_DIR"
echo "------------------------------------------"
