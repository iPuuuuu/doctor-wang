import hashlib
import math
from typing import Protocol


class EmbeddingProvider(Protocol):
    model: str
    dimensions: int
    def embed(self, texts: list[str]) -> list[list[float]]: ...


class FakeEmbeddingProvider:
    def __init__(self, dimensions: int = 32, model: str = "fake-v1"):
        self.dimensions, self.model = dimensions, model

    def embed(self, texts: list[str]) -> list[list[float]]:
        result = []
        for text in texts:
            seed = hashlib.sha256(text.encode()).digest()
            values = [((seed[i % len(seed)] / 255.0) * 2) - 1 for i in range(self.dimensions)]
            norm = math.sqrt(sum(v * v for v in values)) or 1
            result.append([v / norm for v in values])
        return result
