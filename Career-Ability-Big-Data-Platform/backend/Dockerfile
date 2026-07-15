FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17.0.16_8-jre-alpine-3.22
WORKDIR /app
RUN apk upgrade --no-cache \
    && apk add --no-cache wget fontconfig \
    && mkdir -p /usr/share/fonts/noto \
    && mkdir -p /var/lib/career-ability/reports \
    && wget -q -O /usr/share/fonts/noto/NotoSansSC.ttf \
        'https://raw.githubusercontent.com/google/fonts/03781cf7a714af8431d14b6f337f923c774429d7/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf' \
    && echo 'A3041811A78C361B1DE50F953C805E0244951C21C5BD412F7232EF0D899AF0DA  /usr/share/fonts/noto/NotoSansSC.ttf' | sha256sum -c - \
    && fc-cache -f \
    && test -r /usr/share/fonts/noto/NotoSansSC.ttf \
    && addgroup -S app && adduser -S -G app -u 10001 app \
    && chown -R app:app /app /var/lib/career-ability
COPY --from=build /app/target/career-ability-backend-*.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
