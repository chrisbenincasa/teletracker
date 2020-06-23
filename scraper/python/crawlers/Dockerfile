FROM python:3.7-slim

WORKDIR /usr/src/app

COPY requirements.txt ./

RUN pip3 install --no-cache-dir -r requirements.txt

COPY scrapy.cfg ./
COPY crawlers ./crawlers
COPY scripts/run_spider.sh ./run_spider.sh