#!/usr/bin/env bash
# Doctor-Wang 1.6GB VPS 服务器加固：swap + 内核参数（一次执行）
# 用途：避免 OOM 卡死（本次事故根因：无 swap，PostgreSQL+JVM+AI 服务耗尽内存后系统挂起）
set -euo pipefail

echo "==> 创建 2G swap（如无）"
if ! swapon --show | grep -q swapfile; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "    swap 已启用: $(free -m | awk '/Swap/{print $2}')MB"
else
  echo "    swap 已存在"
fi

echo "==> 内核参数（连接队列/文件句柄，缓解半连接堆积）"
cat > /etc/sysctl.d/99-doctor-wang.conf << 'EOF'
# 半连接/连接队列
net.core.somaxconn = 1024
net.ipv4.tcp_max_syn_backlog = 2048
net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_abort_on_overflow = 0
# 文件句柄
fs.file-max = 65536
# 内存：允许 overcommit 防止小内存下 malloc 失败
vm.overcommit_memory = 1
vm.swappiness = 10
EOF
sysctl -p /etc/sysctl.d/99-doctor-wang.conf > /dev/null
echo "    内核参数已应用"

echo "==> Docker 资源守护（容器 OOM 优先被杀，而非整机挂起）"
grep -q '"default-ulimits"' /etc/docker/daemon.json 2>/dev/null || {
  mkdir -p /etc/docker
  echo '{"default-ulimits":{"nofile":{"Name":"nofile","Hard":65536,"Soft":65536}}}' > /etc/docker/daemon.json
  systemctl restart docker || true
  echo "    docker daemon 已配置"
}

echo "==> 完成。当前内存:"
free -m
