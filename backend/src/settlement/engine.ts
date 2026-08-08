// TODO: settlement engine — called from POST /api/v1/sync/transactions (src/api/sync/routes.ts).
// Responsible for the purse-token spend-enforcement checks in
// docs/purse-token-format.md#spend-enforcement-decided: per token_id, verify device_counter /
// prev_tx_hash continuity, aggregate sum(amount) <= token.value, and each amount <= token.cap.
// Not implemented yet — endpoint logic comes next, one endpoint at a time.

export {};
