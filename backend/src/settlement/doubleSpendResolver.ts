// TODO: double-spend resolver — detects a payer's device_counter/prev_tx_hash chain forking
// (the same counter value or prev_tx_hash claimed by two different synced transactions), which
// indicates a replayed or forked offline transaction history. Feeds admin/incidents
// (type=double_spend, see docs/api-contract.md#adminincidents). Not implemented yet.

export {};
