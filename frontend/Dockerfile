FROM node:24.18.0-alpine AS build

WORKDIR /app

# Umi 在 pnpm build 时将地图配置编译进静态资源；最终 nginx 镜像不保留这些构建变量。
ARG AMAP_WEB_JS_KEY
ARG AMAP_WEB_SECURITY_JS_CODE
ENV AMAP_WEB_JS_KEY=${AMAP_WEB_JS_KEY}
ENV AMAP_WEB_SECURITY_JS_CODE=${AMAP_WEB_SECURITY_JS_CODE}

RUN corepack enable
RUN corepack install --global pnpm@10.34.5

COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.28.0-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
