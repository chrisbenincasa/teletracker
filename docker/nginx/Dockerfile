FROM nginx:1.17

RUN apt-get update && apt-get install -y certbot python-certbot-nginx cron

ADD fullchain.pem /etc/letsencrypt/live/api.qa.teletracker.tv/fullchain.pem
ADD privkey.pem /etc/letsencrypt/live/api.qa.teletracker.tv/privkey.pem

RUN mkdir crontab
ADD crontab crontab/crontab
RUN chown -R root crontab && chmod -R 0644 crontab
RUN crontab crontab/crontab

ADD nginx.conf /etc/nginx/nginx.conf