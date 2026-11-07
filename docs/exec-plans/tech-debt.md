# Technical Debt

| Problem | Impact | Location | Priority |
| --- | --- | --- | --- |
| Structured request logging is not implemented | Production diagnosis will be slow | `docs/reliability/errors.md` | Medium |
| The documented error contract is ahead of the implemented response | Clients currently receive `code` and `message`, without `correlationId` or `fieldErrors` | `ApiErrorResponse`; `docs/reliability/errors.md` | Medium |
