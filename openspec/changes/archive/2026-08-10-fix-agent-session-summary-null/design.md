## Context

`application.yml` configures Jackson to omit `null` properties. `AgentSessionResponse.summary` is nullable by design, but omission violates the frontend DTO parser and prevents the initial `/assistant` session bootstrap.

## Goals / Non-Goals

**Goals:**

- Keep the existing nullable `summary` field present in every Agent session response.
- Lock the wire response with a Controller JSON assertion.

**Non-Goals:**

- No session persistence, SSE, model gateway, frontend parser, or global Jackson configuration change.

## Decisions

- Annotate only `AgentSessionResponse` with `@JsonInclude(JsonInclude.Include.ALWAYS)`. This preserves the global `non_null` setting for unrelated APIs while making this established Agent DTO reliable.
- Assert both field existence and JSON null in `AgentControllerTest`; checking the Java record alone would not catch global serialization configuration.

## Risks / Trade-offs

- [A caller relied on omitted `summary`] → The frontend contract and fixtures already require explicit null, so restoring the field is backward-compatible for tolerant consumers.
- [Global configuration changes reintroduce the bug] → Controller serialization test protects the public response.

## Migration Plan

1. Deploy the backend fix.
2. Confirm `POST /api/v1/agent/sessions` returns `data.summary: null` for a newly created session.
3. Re-run the real Agent SSE test; no data migration or frontend rollout is required.

## Open Questions

无。
