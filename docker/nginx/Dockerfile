FROM nginx:1.17

RUN apt-get update && apt-get install -y certbot python-certbot-nginx

ADD fullchain.pem /etc/letsencrypt/live/api.qa.teletracker.tv/fullchain.pem
ADD privkey.pem /etc/letsencrypt/live/api.qa.teletracker.tv/privkey.pem

ADD nginx.conf /etc/nginx/nginx.conf