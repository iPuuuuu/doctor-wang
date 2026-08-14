"""DashScopeOcrProvider 单元测试：请求构造与响应解析（mock HTTP，不触网、不含密钥）。"""
import base64

import app.providers as providers


def test_extract_builds_multimodal_payload_and_parses_pages(monkeypatch):
    captured = {}

    def fake_post_json(endpoint, payload, api_key, timeout):
        captured.update(endpoint=endpoint, payload=payload, api_key=api_key, timeout=timeout)
        return {"choices": [{"message": {"content": "体检报告：收缩压 150mmHg（脱敏）"}}]}

    monkeypatch.setattr(providers, "_post_json", fake_post_json)

    provider = providers.DashScopeOcrProvider(
        endpoint="https://dashscope.example/v1/chat/completions",
        api_key="test-key",
        model="qwen-vl-ocr",
        timeout=30,
    )
    data = b"fake-image-bytes"
    pages = provider.extract(data, "image/png")

    assert pages == [{"pageNo": 1, "text": "体检报告：收缩压 150mmHg（脱敏）", "confidence": 0.9}]
    assert captured["endpoint"] == "https://dashscope.example/v1/chat/completions"
    assert captured["api_key"] == "test-key"
    assert captured["timeout"] == 30
    assert captured["payload"]["model"] == "qwen-vl-ocr"
    content = captured["payload"]["messages"][0]["content"]
    assert content[0]["type"] == "image_url"
    url = content[0]["image_url"]["url"]
    assert url.startswith("data:image/png;base64,")
    assert base64.b64decode(url.split(",", 1)[1]) == b"fake-image-bytes"


def test_extract_uses_provided_content_type_mime(monkeypatch):
    captured = {}

    def fake_post_json(endpoint, payload, api_key, timeout):
        captured["payload"] = payload
        return {"choices": [{"message": {"content": "text"}}]}

    monkeypatch.setattr(providers, "_post_json", fake_post_json)
    provider = providers.DashScopeOcrProvider("http://e", "k", timeout=15)
    provider.extract(b"x", "image/jpeg; charset=binary")
    url = captured["payload"]["messages"][0]["content"][0]["image_url"]["url"]
    assert url.startswith("data:image/jpeg;base64,")


def test_extract_propagates_provider_failure(monkeypatch):
    def boom(endpoint, payload, api_key, timeout):
        raise ConnectionError("provider unreachable")

    monkeypatch.setattr(providers, "_post_json", boom)
    provider = providers.DashScopeOcrProvider("http://e", "k", timeout=15)
    try:
        provider.extract(b"x", "image/png")
        raise AssertionError("expected exception")
    except ConnectionError:
        pass  # 调用方（ingest）将按通用异常处理为 422 / REVIEW_REQUIRED 语义
