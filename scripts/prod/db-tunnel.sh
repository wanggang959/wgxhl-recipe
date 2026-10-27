#!/usr/bin/env bash
# 本机开一条到生产 MySQL 的 SSH 隧道：127.0.0.1:13306 -> 服务器 127.0.0.1:3306
# 生产 MySQL 只监听内网，本地开发/排查时先跑这个，再把连接指向 127.0.0.1:13306。
set -euo pipefail

SSH_HOST="${SSH_HOST:-wgxhl.space}"
SSH_USER="${SSH_USER:-root}"
SSH_KEY="${SSH_KEY:-$HOME/Desktop/ssh-key-154.219.110.161.key}"
LOCAL_PORT="${LOCAL_PORT:-13306}"

echo "建立隧道：127.0.0.1:${LOCAL_PORT} -> ${SSH_HOST}:3306（Ctrl+C 结束）"
exec ssh -i "$SSH_KEY" \
  -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=accept-new \
  -N -L "127.0.0.1:${LOCAL_PORT}:127.0.0.1:3306" \
  "${SSH_USER}@${SSH_HOST}"
