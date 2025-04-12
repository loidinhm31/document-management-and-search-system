# Build stage
FROM node:20-alpine AS build

# Set working directory
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy all project files
COPY . .

# Build app with environment variables injected
RUN npm run build

# Production stage - using lightweight http server
FROM node:20-alpine AS production

# Install serve to run a static server
RUN npm install -g serve

# Copy built app from the build stage
COPY --from=build /app/dist /app

# Expose port
EXPOSE 3000

# Start server
CMD ["serve", "-s", "/app", "-l", "3000"]