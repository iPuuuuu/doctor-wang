import io
from dataclasses import dataclass
from pypdf import PdfReader


@dataclass(frozen=True)
class PageText:
    page_no: int
    text: str
    confidence: float
    method: str


class DocumentExtractor:
    def extract(self, data: bytes, content_type: str) -> list[PageText]:
        raise NotImplementedError


class TextPdfExtractor(DocumentExtractor):
    def extract(self, data: bytes, content_type: str) -> list[PageText]:
        reader = PdfReader(io.BytesIO(data))
        return [PageText(i + 1, (page.extract_text() or "").strip(), 1.0, "text_pdf") for i, page in enumerate(reader.pages)]


class ImageOcrExtractor(DocumentExtractor):
    def extract(self, data: bytes, content_type: str) -> list[PageText]:
        raise NotImplementedError("OCR provider is not configured; result must be REVIEW_REQUIRED")


class DocumentTypeRouter:
    def __init__(self, pdf: DocumentExtractor | None = None, image: DocumentExtractor | None = None):
        self.pdf, self.image = pdf or TextPdfExtractor(), image or ImageOcrExtractor()

    def extract(self, data: bytes, content_type: str) -> list[PageText]:
        if content_type == "application/pdf":
            pages = self.pdf.extract(data, content_type)
            # A PDF with no usable text layer is a scanned PDF. Route it through
            # the OCR extension point; an unavailable OCR provider is handled by
            # the caller as REVIEW_REQUIRED rather than a false success.
            if sum(len(page.text) for page in pages) < 20:
                return self._normalize_ocr(self.image.extract(data, content_type))
            return pages
        if content_type in {"image/jpeg", "image/png"}:
            return self._normalize_ocr(self.image.extract(data, content_type))
        raise ValueError("UNSUPPORTED_CONTENT_TYPE")

    @staticmethod
    def _normalize_ocr(pages) -> list[PageText]:
        return [page if isinstance(page, PageText) else PageText(int(page["pageNo"]), page.get("text", ""), float(page.get("confidence", 0)), "ocr") for page in pages]
