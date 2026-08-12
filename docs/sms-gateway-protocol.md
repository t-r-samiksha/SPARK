# SPARK SMS Gateway Protocol Specification

This document defines the wire format used by Android devices to sync offline transactions via SMS when cellular voice/SMS coverage is available but cellular data/Wi-Fi is down.

**Target Gateway Component:** Member B's SMS Gateway Simulator.

---

## 1. SMS Header & Framing

All SPARK transaction SMS messages carry a deterministic prefix and chunk metadata:

```
SPARK_TX:v1:<part_index>/<total_parts>:<base64url_chunk>
```

### Fields:
- `SPARK_TX:v1:` — Protocol identifier and version marker.
- `<part_index>` — 1-based index of this SMS chunk (e.g. `1`, `2`).
- `<total_parts>` — Total number of SMS chunks required to assemble the complete transaction (e.g. `1`, `2`, `3`).
- `<base64url_chunk>` — Unpadded base64url encoded UTF-8 canonical JSON bytes of the signed `SparkTransaction`.

---

## 2. Examples

### Single-Part Transaction (≤ 120 chars base64url):
```
SPARK_TX:v1:1/1:eyJ0eF9pZCI6IjFhMmIzYzRkLTExMTEtNGEyYi04YzFkLTJlM2Y0YTViNmM3ZCIsImFtb3VudCI6IjI1MDAwIiwic2lnbmF0dXJlIjoiNmRkWX...
```

### Multi-Part Transaction (Chunked):
```
Part 1 of 2:
SPARK_TX:v1:1/2:eyJ0eF9pZCI6IjFhMmIzYzRkLTExMTEtNGEyYi04YzFkLTJlM2Y0YTViNmM3ZCIsInRva2VuX2lkIjoiOWYyYzFhM2UtNWI0ZC00ZTZmLThhMWItMmMzZDRlNWY2YTdi

Part 2 of 2:
SPARK_TX:v1:2/2:IiwicGF5ZXIiOnsiZGV2aWNlX2lkIjoiZGV2LTEiLCJhY2NvdW50X2lkIjoiYWNjLTEiLCJjZXJ0IjoiLS0tLS1CRUdJTiBTUEFSSy...
```

---

## 3. Gateway Ingestion Logic (Pseudocode)

```typescript
function ingestSms(message: string): void {
  if (!message.startsWith("SPARK_TX:v1:")) return;

  const [header, chunkData] = message.replace("SPARK_TX:v1:", "").split(":", 2);
  const [partIndex, totalParts] = header.split("/").map(Number);

  // Buffer chunks until all parts are received for the transaction
  smsBuffer.addChunk(partIndex, totalParts, chunkData);

  if (smsBuffer.isComplete()) {
    const fullBase64Url = smsBuffer.reassemble();
    const jsonString = Buffer.from(fullBase64Url, 'base64url').toString('utf8');
    const transaction = JSON.parse(jsonString);

    // Settle transaction via POST /api/v1/sync/transactions
    settleTransaction(transaction);
  }
}
```
