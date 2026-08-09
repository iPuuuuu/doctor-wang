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


class ConfiguredOcrProvider:
    def __init__(self, endpoint: str, api_key: str | None, timeout: float = 15): self.endpoint, self.api_key, self.timeout = endpoint, api_key, timeout

    def extract(self, data: bytes, content_type: str):
        import base64
        body = _post_json(self.endpoint, {"contentType": content_type, "fileBase64": base64.b64encode(data).decode()}, self.api_key, self.timeout)
        return body["pages"]


class OpenAICompatibleGenerationProvider:
    def __init__(self, endpoint: str, api_key: str | None, model: str, timeout: float = 15): self.endpoint, self.api_key, self.model, self.timeout = endpoint, api_key, model, timeout

    def generate(self, question: str, context: str) -> str:
        body = _post_json(self.endpoint, {"model": self.model, "temperature": 0, "messages": [{"role": "system", "content": "只基于提供的来源回答；不能诊断。"}, {"role": "user", "content": f"来源:\n{context}\n问题:{question}"}]}, self.api_key, self.timeout)
        return body["choices"][0]["message"]["content"]
