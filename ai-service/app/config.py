from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="DW_AI_", extra="ignore")
    internal_token: str | None = None
    embedding_model: str = "fake-v1"
    embedding_dimensions: int = 32
    parser_version: str = "pdf-parser-1.0.0"
    prompt_version: str = "rag-1.0.0"
    safety_policy_version: str = "safety-1.0.0"
    extraction_min_chars: int = 20
    ocr_min_confidence: float = 0.75
    database_url: str | None = None
    job_state_path: str = ".data/jobs.sqlite3"
    object_store_root: str = ".data/objects"
    ocr_endpoint: str | None = None
    ocr_api_key: str | None = None
    ocr_model: str = "qwen-vl-ocr"
    embedding_endpoint: str | None = None
    embedding_api_key: str | None = None
    llm_endpoint: str | None = None
    llm_api_key: str | None = None
    llm_model: str = "configured-model"
    provider_timeout_seconds: float = 60.0


settings = Settings()
