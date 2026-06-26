FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY src ./src
COPY public ./public

RUN mkdir -p out \
    && find src -name "*.java" > sources.txt \
    && javac -d out @sources.txt

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
ENV PORT=8080

COPY --from=build /app/out ./out
COPY --from=build /app/public ./public

EXPOSE 8080

CMD ["java", "-cp", "out", "oop.blog.presentation.BlogApiServer"]
