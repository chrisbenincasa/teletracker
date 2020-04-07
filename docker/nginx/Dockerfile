FROM nginx:1.17

RUN apt-get update && apt-get install -y certbot python-certbot-nginx cron

RUN mkdir -p /etc/letsencrypt

RUN mkdir crontab
ADD crontab crontab/crontab
RUN chown -R root crontab && chmod -R 0644 crontab
RUN crontab crontab/crontab

ADD nginx.conf /etc/nginx/nginx.conf