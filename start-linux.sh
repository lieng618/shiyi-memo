#!/usr/bin/env bash
# 一键构建部署（Linux 服务器）：前端构建 -> 后端打包（jar 输出到 start/）-> pm2 启动 / 重启
# 用法：在项目根目录执行  bash start-linux.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
START_DIR="$ROOT/start"
JAR_NAME="shiyi-memo-1.0.0.jar"

cd "$ROOT"

# 让 pm2 和脚本用同一个应用目录
export APP_DIR="${APP_DIR:-$START_DIR}"
export APP_JAR="${APP_JAR:-$JAR_NAME}"

command -v npm >/dev/null 2>&1 || { echo "未找到 npm，请先安装 Node.js 20+" >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "未找到 mvn，请先安装 Maven 3.9+" >&2; exit 1; }
command -v pm2 >/dev/null 2>&1 || { echo "未找到 pm2，请先执行 npm i -g pm2" >&2; exit 1; }

echo "==> [1/3] 构建前端"
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  (cd "$ROOT/frontend" && npm install)
fi
(cd "$ROOT/frontend" && npm run build)

echo "==> [2/3] 打包后端（jar 输出到 start/）"
mkdir -p "$START_DIR/logs"
mvn -B -DskipTests clean package

if [ ! -f "$START_DIR/$JAR_NAME" ]; then
  echo "打包结束但未找到 $START_DIR/$JAR_NAME，请检查 maven 配置" >&2
  exit 1
fi

echo "==> [3/3] pm2 启动 / 重启服务"
pm2 startOrReload "$ROOT/ecosystem.config.js" --update-env
pm2 save

echo
echo "完成：$(date '+%F %T')"
echo "应用目录：$APP_DIR"
echo "jar 文件：$START_DIR/$JAR_NAME"
echo "访问地址：http://localhost:8080"
pm2 status
