# Stage 1: Download and prepare tessdata
FROM alpine:3.18 AS tessdata

# Install wget to download language data
RUN apk add --no-cache wget

# Create tessdata directory
RUN mkdir -p /usr/share/tessdata

# Download specific versions of language training data and osd
RUN wget -O /usr/share/tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata && \
    wget -O /usr/share/tessdata/vie.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/vie.traineddata && \
    wget -O /usr/share/tessdata/osd.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/osd.traineddata && \
    wget -O /usr/share/tessdata/kor.traineddata https://github.com/tesseract-ocr/tessdata_best/raw/main/kor.traineddata

# Stage 2: Build the application
FROM maven:3.9.5-amazoncorretto-17 AS builder

WORKDIR /app
COPY pom.xml .
# Download dependencies first (cache layer)
RUN mvn dependency:go-offline

COPY src src
RUN mvn clean package -DskipTests

# Stage 3: Final stage
FROM amazoncorretto:17-alpine

# Install required dependencies
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-vie \
    fontconfig \
    ttf-dejavu \
    graphicsmagick \
    tini \
    tzdata \
    musl-locales \
    && rm -rf /var/cache/apk/*

# Copy tessdata from first stage
COPY --from=tessdata /usr/share/tessdata /usr/share/tessdata

# Copy application
COPY --from=builder /app/target/*.jar app.jar

# Set environment variables
ENV TESSDATA_PREFIX=/usr/share/tessdata \
    APP_OCR_DATA_PATH=/usr/share/tessdata \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    TZ=UTC \
    JAVA_OPTS="-XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
               -Dfile.encoding=UTF-8 \
               -Dsun.jnu.encoding=UTF-8 \
               -Dclient.encoding.override=UTF-8"

# Create necessary directories with proper permissions
RUN mkdir -p /tmp/ocr  && \
    chmod 755 /tmp/ocr

# Create non-root user
RUN addgroup -S spring && \
    adduser -S spring -G spring && \
    chown -R spring:spring /tmp/ocr

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8084

# Use tini as init process
ENTRYPOINT ["/sbin/tini", "--"]

# Set the application command
CMD ["sh", "-c", "java $JAVA_OPTS \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=$TZ \
    -jar app.jar"]