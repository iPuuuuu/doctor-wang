#!/usr/bin/env bash
# Doctor-Wang 生产端到端冒烟测试（部署后/巡检用一键验证）
# 用法：bash smoke-test.sh [BASE_URL]   （默认 https://120.55.186.220）
# 覆盖：健康检查 → demo 登录 → 报告列表/详情解读 → 安心话聊 → 普通问诊 → 注册 → 登出。
set -uo pipefail

BASE="${1:-https://120.55.186.220}"
PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }

echo "== Doctor-Wang 冒烟测试: $BASE =="

echo "--- 1. 健康检查 ---"
curl -sk -m 10 "$BASE/healthz" | grep -q '"ok"' && ok "healthz" || bad "healthz"
curl -sk -m 10 "$BASE/actuator/health" | grep -q '"UP"' && ok "actuator" || bad "actuator"

echo "--- 2. demo 登录 ---"
LOGIN=$(curl -sk -m 15 -X POST "$BASE/api/login" -H 'Content-Type: application/json' \
  -d '{"account":"demo@doctorwang.app","password":"123456"}' 2>/dev/null)
TOKEN=$(printf '%s' "$LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))' 2>/dev/null)
[ -n "$TOKEN" ] && ok "登录成功" || { bad "登录失败: $LOGIN"; exit 1; }

echo "--- 3. 报告列表 + 详情解读 ---"
LIST=$(curl -sk -m 10 "$BASE/api/v1/reports" -H "Authorization: Bearer $TOKEN")
CNT=$(printf '%s' "$LIST" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("totalElements",0))' 2>/dev/null)
[ "${CNT:-0}" -ge 1 ] && ok "报告列表 ($CNT 份)" || bad "报告列表为空"
RID=$(printf '%s' "$LIST" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["items"][0]["reportId"])' 2>/dev/null)
DETAIL=$(curl -sk -m 15 "$BASE/api/v1/reports/$RID" -H "Authorization: Bearer $TOKEN")
SUMLEN=$(printf '%s' "$DETAIL" | python3 -c 'import sys,json; print(len(json.load(sys.stdin).get("summary","")))' 2>/dev/null)
if [ "${SUMLEN:-0}" -gt 20 ] && ! printf '%s' "$DETAIL" | grep -q "报告正在处理"; then ok "详情解读已生成 ($SUMLEN 字)"; else bad "详情解读缺失"; fi

echo "--- 4. 安心话聊 ---"
CHAT=$(curl -sk -m 90 -X POST "$BASE/api/v1/chat/anxiety" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"我最近总担心身体出问题"}' 2>/dev/null)
CT=$(printf '%s' "$CHAT" | python3 -c 'import sys,json; print(len(json.load(sys.stdin).get("message","")))' 2>/dev/null)
[ "${CT:-0}" -gt 20 ] && ok "安心话聊回复 ($CT 字)" || bad "安心话聊异常: $CHAT"

echo "--- 5. 普通问诊 ---"
QA=$(curl -sk -m 90 -X POST "$BASE/api/chat" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"我的报告主要查了什么？"}' 2>/dev/null)
QLEN=$(printf '%s' "$QA" | python3 -c 'import sys,json; print(len(json.load(sys.stdin).get("message","")))' 2>/dev/null)
[ "${QLEN:-0}" -gt 10 ] && ok "普通问诊回复 ($QLEN 字)" || bad "普通问诊异常"

echo "--- 6. 注册 + 登出 ---"
ACC="smoke-$(date +%s)@test.local"
REG=$(curl -sk -m 15 -X POST "$BASE/api/register" -H 'Content-Type: application/json' \
  -d "{\"account\":\"$ACC\",\"password\":\"secret123\",\"name\":\"冒烟\",\"age\":30,\"height\":170,\"weight\":65}" 2>/dev/null)
RT=$(printf '%s' "$REG" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))' 2>/dev/null)
[ -n "$RT" ] && ok "注册成功" || bad "注册失败"
LO=$(curl -sk -m 10 -X POST "$BASE/api/logout" -H "Authorization: Bearer $TOKEN" -o /dev/null -w "%{http_code}")
[ "$LO" = "204" ] && ok "登出 (204)" || bad "登出失败: $LO"
AFTER=$(curl -sk -m 10 "$BASE/api/profile" -H "Authorization: Bearer $TOKEN" -o /dev/null -w "%{http_code}")
[ "$AFTER" = "401" ] && ok "登出后 token 失效 (401)" || bad "token 未失效: $AFTER"

echo ""
echo "== 结果: $PASS 通过 / $FAIL 失败 =="
[ "$FAIL" -eq 0 ] && echo "✅ 全部通过" || echo "❌ 存在失败项"
exit $FAIL
