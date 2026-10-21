# Rotalog API Notificações

Notifications microservice of the Rotalog system, built with .NET 6 and ASP.NET Core. It sends email and SMS notifications, manages message templates and keeps the sending history, storing its data in the `notificacoes` schema of the shared PostgreSQL database through Entity Framework Core. The code follows a Controller -> Service -> DbContext structure and exposes Swagger documentation.

## Prerequisites

Before starting, make sure you've met the following requirements:

- You have installed version 6 of the `.NET SDK`.
- You have the Rotalog database running (see `rotalog-workspace/README.md`).

## Installing the Project

There are no EF Core migrations: the database is created by `rotalog-workspace`, and the API calls `EnsureCreated()` on startup.

1. Navigate to the project directory

    ```bash
    cd rotalog-api-notificacoes
    ```

2. Restore the dependencies

    ```bash
    dotnet restore
    ```

3. Configure the connection string and providers in `appsettings.json`

    ```json
    {
      "ConnectionStrings": {
        "DefaultConnection": "Host=localhost;Port=5432;Database=rotalog;Username=rotalog_admin;Password=ENTER YOUR DB PASSWORD;SearchPath=notificacoes"
      },
      "EmailSettings": {
        "SmtpServer": "smtp.gmail.com",
        "SmtpPort": 587,
        "SenderEmail": "noreply@rotalog.com",
        "SenderPassword": "ENTER YOUR SMTP PASSWORD"
      },
      "SmsSettings": {
        "ApiKey": "ENTER YOUR SMS API KEY",
        "ApiUrl": "https://api.smsprovider.com/send"
      }
    }
    ```

4. Build the project

    ```bash
    dotnet build
    ```

## Using the Project

To use Rotalog API Notificações, follow these steps:

```bash
dotnet run
curl http://localhost:5000/api/health
```

Swagger UI is available at `http://localhost:5000/swagger`. Main endpoints:

```text
GET    /api/health
GET    /api/notificacoes
GET    /api/notificacoes/{id}
POST   /api/notificacoes
POST   /api/notificacoes/{id}/reenviar
GET    /api/notificacoes/stats
GET    /api/notificacoes/templates
POST   /api/notificacoes/processar
```

Other useful commands:

```bash
# Run with hot reload
dotnet watch run

# List notification templates
curl http://localhost:5000/api/notificacoes/templates
```

Ready-made requests for every endpoint are available in `requests.http` (VS Code REST Client or JetBrains HTTP Client).
