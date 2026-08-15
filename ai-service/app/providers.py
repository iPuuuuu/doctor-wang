"""Configuration-driven HTTP providers; no vendor SDK or secret is hard-coded."""
import json
from urllib.request import Request, urlopen


def _post_json(endpoint: str, payload: dict, api_key: str | None, timeout: float) -> dict:
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    request = Request(endpoint, data=json.dumps(payload).encode(), headers=headers, method="POST")
    with urlopen(request, timeout=timeout) as response:
        return json.loads(response.read())


class OpenAICompatibleEmbeddingProvider:
    def __init__(self, endpoint: str, api_key: str | None, model: str, dimensions: int, timeout: float = 15):
        self.endpoint, self.api_key, self.model, self.dimensions, self.timeout = endpoint, api_key, model, dimensions, timeout

    def embed(self, texts: list[str]) -> list[list[float]]:
        body = _post_json(self.endpoint, {"model": self.model, "input": texts}, self.api_key, self.timeout)
        values = [item["embedding"] for item in sorted(body["data"], key=lambda item: item.get("index", 0))]
        if values and len(values[0]) != self.dimensions:
            raise ValueError("EMBEDDING_DIMENSION_MISMATCH")
        return values


class DashScopeOcrProvider:
    """DashScope (Tongyi) qwen-vl-ocr adapter via the OpenAI-compatible multimodal chat endpoint.

    Returns the DocumentTypeRouter contract: a list of {"pageNo", "text", "confidence"}.
    qwen-vl-ocr does not expose per-page confidence; 0.9 is a conservative fixed default.
    """

    def __init__(self, endpoint: str, api_key: str | None, model: str = "qwen-vl-ocr", timeout: float = 30):
        self.endpoint, self.api_key, self.model, self.timeout = endpoint, api_key, model, timeout

    def extract(self, data: bytes, content_type: str) -> list[dict]:
        import base64
        mime = (content_type or "image/png").split(";")[0].strip()
        body = _post_json(
            self.endpoint,
            {
                "model": self.model,
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:{mime};base64,{base64.b64encode(data).decode()}"},
                            }
                        ],
                    }
                ],
            },
            self.api_key,
            self.timeout,
        )
        text = body["choices"][0]["message"]["content"]
        return [{"pageNo": 1, "text": text, "confidence": 0.9}]


class OpenAICompatibleGenerationProvider:
    def __init__(self, endpoint: str, api_key: str | None, model: str, timeout: float = 15): self.endpoint, self.api_key, self.model, self.timeout = endpoint, api_key, model, timeout

    DEFAULT_SYSTEM_PROMPT = "只基于提供的来源回答；不能诊断。"

    def generate(self, question: str, context: str, system_prompt: str | None = None) -> str:
        body = _post_json(
            self.endpoint,
            {
                "model": self.model,
                "temperature": 0,
                "messages": [
                    {"role": "system", "content": system_prompt or self.DEFAULT_SYSTEM_PROMPT},
                    {"role": "user", "content": f"来源:\n{context}\n问题:{question}"},
                ],
            },
            self.api_key,
            self.timeout,
        )
        return body["choices"][0]["message"]["content"]
