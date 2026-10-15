"""Generation provider compatibility tests, including AgentOps response metadata."""
import app.providers as providers


def test_generation_provider_preserves_medical_system_prompt_and_limits(monkeypatch):
    captured = {}

    def fake_post_json(endpoint, payload, api_key, timeout):
        captured.update(endpoint=endpoint, payload=payload, api_key=api_key, timeout=timeout)
        return {
            "trace_id": "agentops-trace-1",
            "provider": "primary-provider",
            "fallback": False,
            "choices": [{"message": {"role": "assistant", "content": "请结合医生意见复核。"}}],
        }

    monkeypatch.setattr(providers, "_post_json", fake_post_json)
    provider = providers.OpenAICompatibleGenerationProvider(
        "http://agentops:8080/v1/chat/completions", None, "balanced", timeout=60
    )

    answer = provider.generate(
        "这项指标是什么意思？",
        "来源内容（脱敏）",
        system_prompt="只基于来源回答，不能诊断。",
        max_tokens=200,
    )

    assert answer == "请结合医生意见复核。"
    assert captured["endpoint"] == "http://agentops:8080/v1/chat/completions"
    assert captured["api_key"] is None
    assert captured["payload"] == {
        "model": "balanced",
        "temperature": 0,
        "messages": [
            {"role": "system", "content": "只基于来源回答，不能诊断。"},
            {"role": "user", "content": "来源:\n来源内容（脱敏）\n问题:这项指标是什么意思？"},
        ],
        "max_tokens": 200,
    }
