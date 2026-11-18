# Build stage: installs the exact versions from package-lock.json and builds the app.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Where the Next.js server forwards /api calls (see next.config.ts). It is read at build time, so
# it is set here: "app" is the Spring Boot service's name on the compose network.
ARG BACKEND_URL=http://app:8080
ENV BACKEND_URL=$BACKEND_URL
RUN npm run build

# Run stage: only the small standalone server Next.js builds, plus the page assets. No
# node_modules or source code, and it runs as the image's non-root "node" user.
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
