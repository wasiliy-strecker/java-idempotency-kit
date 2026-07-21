# Contributing

Open an issue before changing persisted states, public exceptions, or acquisition semantics. Store implementations must pass the shared contract and preserve raw-key confidentiality.

Run the local verification:

```bash
./mvnw spotless:apply
./mvnw verify
```

Run `./mvnw -Pintegration verify` on a machine with Docker before proposing PostgreSQL changes.
