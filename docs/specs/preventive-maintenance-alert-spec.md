## Preventive Maintenance Alert

### Acceptance Criteria:

- rotalog-api-frotas: endpoint that checks for vehicles with mileage > limit or date > x months
- rotalog-api-frotas: call to rotalog-api-notificacoes when an eligible vehicle is found
- rotalog-api-notificacoes: endpoint that receives an alert request and sends an email to the manager
- rotalog-api-notificacoes: returns the notification status (sent/failed)
- rotalog-api-frotas: records the alert in the database with the notification status
- painel-admin: simple page like the maitenance one that lists alerts, with a filter by status
- tests: unit tests in each service + end-to-end tests of the complete flow
- communication: synchronous HTTP between the services
- failure handling: if rotalog-api-notificacoes is down, the alert is recorded with status "pending"