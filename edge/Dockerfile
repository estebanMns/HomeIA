# Versión fijada (actualizar de forma deliberada y probar antes de desplegar).
FROM python:3.12.12-slim-bookworm
WORKDIR /app
RUN useradd --system --uid 10001 --home /var/lib/homeia-edge homeia-edge \
    && mkdir -p /var/lib/homeia-edge && chown homeia-edge /var/lib/homeia-edge && chmod 700 /var/lib/homeia-edge
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY homeia_edge ./homeia_edge
ENV PYTHONUNBUFFERED=1 HOMEIA_EDGE_DATA_DIR=/var/lib/homeia-edge
USER homeia-edge
VOLUME ["/var/lib/homeia-edge"]
CMD ["python", "-m", "homeia_edge"]
