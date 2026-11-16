FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY homeia_edge ./homeia_edge
ENV PYTHONUNBUFFERED=1
CMD ["python", "-m", "homeia_edge"]
