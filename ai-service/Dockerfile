# Development/integration image for the controlled AI service.
FROM python:3.11-slim

WORKDIR /app
COPY pyproject.toml README.md ./
COPY app ./app
# PIP_INDEX_URL is overridable (e.g. Aliyun mirror for mainland-China servers):
#   docker compose build --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ARG PIP_INDEX_URL=https://pypi.org/simple
RUN pip install --no-cache-dir --index-url ${PIP_INDEX_URL} .

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
