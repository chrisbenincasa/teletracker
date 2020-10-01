FROM node:12 as build

ARG version
ENV VERSION $version

WORKDIR /usr/src/app

ADD package.json .
ADD yarn.lock .
ADD tsconfig.json .
ADD next.config.js .
ADD .env .

RUN yarn

ADD next-env.d.ts .
ADD server.js .
ADD src/ src/
ADD public/ public/

RUN yarn run next-build

FROM gcr.io/distroless/nodejs:12
COPY --from=build /usr/src/app /

ENV NODE_ENV production
CMD ["server.js"]