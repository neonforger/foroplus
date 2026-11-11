FROM python:3.12-slim
# git: pip instala el fork de SpotAPI desde GitHub.
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY emparejar.py conversor.py ./
CMD ["python", "-u", "conversor.py"]
