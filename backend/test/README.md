# Isolated MySQL integration tests

The test database is a local Docker container on 127.0.0.1:13307. It uses
test-only credentials and does not connect to development or production data.
The SQL migrations run on the first initialization of its Docker volume.

From WSL, at the repository root:

```sh
docker compose -f backend/test/docker-compose.yml up -d --wait
```

From the backend directory, run `mvn -Dtest.integration.enabled=true test`.
Real SMTP stays disabled unless explicitly enabled separately. Ordinary
`mvn test` runs unit tests without starting Spring or accessing a database.

The container data remains in a dedicated Docker volume; stop the container
with `docker compose -f backend/test/docker-compose.yml stop`. Reset the volume
only after verifying it contains no needed data.
