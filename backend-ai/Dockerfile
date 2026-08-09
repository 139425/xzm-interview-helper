FROM python:3.11-slim

ARG DEBIAN_MIRROR=http://deb.debian.org/debian
ARG DEBIAN_SECURITY_MIRROR=http://deb.debian.org/debian-security
ARG PIP_INDEX_URL=https://pypi.org/simple

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN sed -i \
      -e "s|http://deb.debian.org/debian-security|${DEBIAN_SECURITY_MIRROR}|g" \
      -e "s|http://deb.debian.org/debian|${DEBIAN_MIRROR}|g" \
      /etc/apt/sources.list.d/debian.sources \
  && apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=20 \
       -o Acquire::https::Timeout=20 update \
  && apt-get install -y --no-install-recommends \
    build-essential \
  && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt
RUN python -m pip install --index-url "$PIP_INDEX_URL" --timeout 20 --retries 3 -U pip \
  && python -m pip install --index-url "$PIP_INDEX_URL" --timeout 20 --retries 3 \
       -r /app/requirements.txt

COPY . /app

EXPOSE 9090
EXPOSE 50051

CMD ["python", "main.py"]
