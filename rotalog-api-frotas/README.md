# Rotalog API Frotas

Fleet management microservice of the Rotalog system, built with Java 11 and Spring Boot 2.7. It manages vehicles (`veiculos`), drivers (`motoristas`) and maintenance records (`manutencoes`), and stores its data in the `frotas` schema of the shared PostgreSQL database. The code follows a Controller -> Service -> Repository structure with JPA entities.

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed version 11 of the `JDK`.
- You have installed version 3.6+ of `Maven`.
- You have the Rotalog database running (see `rotalog-workspace/README.md`).

## Installing the Project

The database schema and seed data are created by `rotalog-workspace`, so Flyway is disabled in this service.

1. Navigate to the project directory

    ```bash
    cd rotalog-api-frotas
    ```

2. Check the database connection in `src/main/resources/application.properties`

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/rotalog?currentSchema=frotas
    spring.datasource.username=rotalog_admin
    spring.datasource.password=ENTER YOUR DB PASSWORD
    ```

3. Build the project

    ```bash
    mvn clean install
    ```

## Using the Project

To use Rotalog API Frotas, follow these steps:

```bash
mvn spring-boot:run
curl http://localhost:8080/api/health
```

The API is served at `http://localhost:8080/api`. Main endpoints:

```text
GET    /api/veiculos
GET    /api/veiculos/placa/{placa}
GET    /api/veiculos/status/{status}
GET    /api/veiculos/estatisticas
POST   /api/veiculos
PUT    /api/veiculos/{id}
PATCH  /api/veiculos/{id}/quilometragem
PATCH  /api/veiculos/{id}/desativar | /reativar

GET    /api/motoristas
GET    /api/motoristas/{id}
GET    /api/motoristas/cnh/{cnh}
GET    /api/motoristas/status/{status}
GET    /api/motoristas/cnh-vencida
POST   /api/motoristas
PUT    /api/motoristas/{id}
PATCH  /api/motoristas/{id}/desativar | /reativar

GET    /api/manutencoes
GET    /api/manutencoes/{id}
GET    /api/manutencoes/veiculo/{veiculoId}
GET    /api/manutencoes/veiculo/{veiculoId}/ultima
GET    /api/manutencoes/pendentes
POST   /api/manutencoes
PATCH  /api/manutencoes/{id}/iniciar | /concluir | /cancelar
```

Other useful commands:

```bash
# List all vehicles
curl http://localhost:8080/api/veiculos

# Package and run the jar
mvn clean package
java -jar target/api-frotas-1.0-SNAPSHOT.jar

# Run tests
mvn test
```
