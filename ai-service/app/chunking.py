import hashlib
from dataclasses import dataclass
from .parsing import PageText


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    text: str
    page_no: int
    confidence: float
    content_hash: str


def chunk_pages(pages: list[PageText], max_chars: int = 900, overlap: int = 120) -> list[Chunk]:
    chunks: list[Chunk] = []
    for page in pages:
        text = page.text
        start = 0
        while start < len(text):
            end = min(start + max_chars, len(text))
            body = text[start:end].strip()
            if body:
                digest = hashlib.sha256(body.encode()).hexdigest()
                chunks.append(Chunk(f"p{page.page_no}-{digest[:16]}", body, page.page_no, page.confidence, digest))
            if end == len(text):
                break
            start = max(end - overlap, start + 1)
    return chunks
