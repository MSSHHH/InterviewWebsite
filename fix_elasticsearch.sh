#!/bin/bash

# Elasticsearch 修复脚本
# 解决 Homebrew Elasticsearch 后台启动错误问题

echo "=========================================="
echo "  Elasticsearch 修复脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ES_ENV_FILE="/opt/homebrew/etc/elasticsearch/elasticsearch-env"

echo -e "${BLUE}检查问题...${NC}"

# 检查 Java
echo "1. 检查 Java 环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    echo -e "   ${GREEN}✓${NC} Java 已安装: $JAVA_VERSION"
else
    echo -e "   ${RED}✗${NC} Java 未安装"
    exit 1
fi

# 检查 Java 11
echo "2. 检查 Java 11..."
if [ -d "/opt/homebrew/opt/openjdk@11" ]; then
    echo -e "   ${GREEN}✓${NC} openjdk@11 已安装"
    JAVA_11_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
    if [ -f "$JAVA_11_HOME/bin/java" ]; then
        echo -e "   ${GREEN}✓${NC} Java 11 可执行文件存在"
    else
        echo -e "   ${YELLOW}⚠${NC} Java 11 路径不正确"
        JAVA_11_HOME=""
    fi
else
    echo -e "   ${YELLOW}⚠${NC} openjdk@11 未安装"
    JAVA_11_HOME=""
fi

# 检查当前 Java 版本
echo "3. 检查当前 Java 版本..."
CURRENT_JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
MAJOR_VERSION=$(echo $CURRENT_JAVA_VERSION | cut -d. -f1)

if [ "$MAJOR_VERSION" -ge 11 ]; then
    echo -e "   ${GREEN}✓${NC} Java 版本符合要求 (>= 11)"
    USE_SYSTEM_JAVA=true
else
    echo -e "   ${YELLOW}⚠${NC} Java 版本可能不符合要求 (当前: $CURRENT_JAVA_VERSION)"
    USE_SYSTEM_JAVA=false
fi

echo ""
echo -e "${BLUE}解决方案选择：${NC}"
echo "1. 安装 openjdk@11 并配置（推荐）"
echo "2. 使用系统 Java（如果版本 >= 11）"
echo "3. 查看当前配置"
echo ""
read -p "请选择 (1-3): " choice

case $choice in
    1)
        echo ""
        echo -e "${BLUE}安装 openjdk@11...${NC}"
        if brew install openjdk@11 2>&1 | tee /tmp/install_java.log; then
            echo -e "${GREEN}✓${NC} openjdk@11 安装成功"
            
            # 配置 ES_JAVA_HOME
            JAVA_11_HOME="/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home"
            if [ -f "$JAVA_11_HOME/bin/java" ]; then
                echo ""
                echo -e "${BLUE}配置 ES_JAVA_HOME...${NC}"
                
                # 备份原配置
                if [ -f "$ES_ENV_FILE" ]; then
                    cp "$ES_ENV_FILE" "${ES_ENV_FILE}.backup"
                    echo -e "${GREEN}✓${NC} 已备份原配置"
                fi
                
                # 写入新配置
                echo "export ES_JAVA_HOME=\"$JAVA_11_HOME\"" > "$ES_ENV_FILE"
                echo -e "${GREEN}✓${NC} ES_JAVA_HOME 已配置: $JAVA_11_HOME"
            else
                echo -e "${RED}✗${NC} Java 11 路径不正确，请手动配置"
            fi
        else
            echo -e "${RED}✗${NC} 安装失败，请查看日志: /tmp/install_java.log"
            exit 1
        fi
        ;;
    2)
        if [ "$USE_SYSTEM_JAVA" = true ]; then
            echo ""
            echo -e "${BLUE}使用系统 Java...${NC}"
            
            # 获取系统 Java Home
            SYSTEM_JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
            if [ -z "$SYSTEM_JAVA_HOME" ]; then
                SYSTEM_JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
            fi
            
            if [ -f "$SYSTEM_JAVA_HOME/bin/java" ]; then
                # 备份原配置
                if [ -f "$ES_ENV_FILE" ]; then
                    cp "$ES_ENV_FILE" "${ES_ENV_FILE}.backup"
                    echo -e "${GREEN}✓${NC} 已备份原配置"
                fi
                
                # 写入新配置
                echo "export ES_JAVA_HOME=\"$SYSTEM_JAVA_HOME\"" > "$ES_ENV_FILE"
                echo -e "${GREEN}✓${NC} ES_JAVA_HOME 已配置: $SYSTEM_JAVA_HOME"
            else
                echo -e "${RED}✗${NC} 无法找到系统 Java Home"
                exit 1
            fi
        else
            echo -e "${RED}✗${NC} 系统 Java 版本不符合要求，请选择方案 1"
            exit 1
        fi
        ;;
    3)
        echo ""
        echo -e "${BLUE}当前配置：${NC}"
        if [ -f "$ES_ENV_FILE" ]; then
            cat "$ES_ENV_FILE"
        else
            echo "配置文件不存在"
        fi
        exit 0
        ;;
    *)
        echo -e "${RED}无效选择${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}重启 Elasticsearch 服务...${NC}"
brew services stop elasticsearch-full 2>/dev/null
sleep 2
if brew services start elasticsearch-full; then
    echo -e "${GREEN}✓${NC} Elasticsearch 启动成功"
    sleep 3
    
    # 验证
    if curl -s http://localhost:9200 > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Elasticsearch 运行正常"
        echo ""
        echo "测试连接:"
        curl -s http://localhost:9200 | head -5
    else
        echo -e "${YELLOW}⚠${NC} Elasticsearch 可能还在启动中，请稍后检查"
        echo "查看日志: tail -f /opt/homebrew/var/log/elasticsearch/elasticsearch_mhhh.log"
    fi
else
    echo -e "${RED}✗${NC} Elasticsearch 启动失败"
    echo "查看日志: tail -f /opt/homebrew/var/log/elasticsearch.log"
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}修复完成！${NC}"
echo "=========================================="
