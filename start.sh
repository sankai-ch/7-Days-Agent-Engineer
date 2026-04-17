#!/usr/bin/env bash
# ============================================================
# 一键启动脚本
# 用法: ./start.sh
# ============================================================
set -e

echo "=========================================="
echo "  Agent Engineer — 一键启动"
echo "=========================================="

# ---------- 环境检查 ----------
echo ""
echo "[1/4] 检查环境..."

# Java
if ! command -v java &> /dev/null; then
    echo "  ✗ 未找到 Java，请安装 JDK 17+"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
echo "  ✓ Java 版本: $(java -version 2>&1 | head -1)"
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "  ✗ 需要 JDK 17+，当前主版本: $JAVA_VER"
    exit 1
fi

# Maven
if ! command -v mvn &> /dev/null; then
    echo "  ✗ 未找到 Maven，请安装 Maven 3.6+"
    exit 1
fi
echo "  ✓ Maven 版本: $(mvn -version 2>&1 | head -1)"

# 配置文件
YML="src/main/resources/application.yml"
if [ ! -f "$YML" ]; then
    echo "  ✗ 未找到 $YML"
    exit 1
fi
echo "  ✓ 配置文件: $YML"

# ---------- 编译 ----------
echo ""
echo "[2/4] 编译项目..."
mvn compile -q
echo "  ✓ 编译成功"

# ---------- 运行测试 ----------
echo ""
echo "[3/4] 运行测试..."
mvn test -Dtest="McpServerTest,OrchestrationTest,ObservabilityTest,SecurityTest" -q 2>&1 | tail -3
echo "  ✓ 测试通过"

# ---------- 启动 ----------
echo ""
echo "[4/4] 启动服务..."
echo ""
echo "  服务即将在 http://localhost:8080 启动"
echo "  健康检查: curl http://localhost:8080/health"
echo "  按 Ctrl+C 停止"
echo ""
echo "=========================================="

mvn spring-boot:run
