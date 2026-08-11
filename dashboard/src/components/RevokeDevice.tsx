import { useMemo, useState } from 'react';
import { ApiError, revokeDevice } from '../lib/api';
import type { RevokeBody, RevokeResult } from '../lib/types';
import { formatEpoch, isValidUuidV4, shortId } from '../lib/format';
import { useMutation } from '../hooks/useAsync';
import { Button } from './ui/Button';
import { Feedback } from './ui/Feedback';
import { Field } from './ui/Field';
import { TextInput } from './ui/TextInput';
import { Tag } from './ui/Tag';

const AUDIT_REASON_PRESETS = [
  'Device reported stolen / lost hardware',
  'Firmware enclave integrity breach',
  'Double-spend sequence slot collision',
  'Compromised device private key',
];

type RevokeStage = 'identify' | 'confirm' | 'done';

export interface RevokeDeviceProps {
  initialDeviceId?: string;
}

export function RevokeDevice({ initialDeviceId = '' }: RevokeDeviceProps) {
  const [deviceId, setDeviceId] = useState(initialDeviceId);
  const [reason, setReason] = useState('');
  const [confirmChallenge, setConfirmChallenge] = useState('');
  const [stage, setStage] = useState<RevokeStage>('identify');
  const [result, setResult] = useState<RevokeResult | null>(null);

  // Sync if initialDeviceId changes from parent
  useMemo(() => {
    if (initialDeviceId) {
      setDeviceId(initialDeviceId);
      if (!reason) {
        setReason('Double-spend sequence slot collision detected in settlement mesh');
      }
    }
  }, [initialDeviceId]);

  const { run, pending, error } = useMutation((body: RevokeBody) => revokeDevice(body));

  const idValid = isValidUuidV4(deviceId.trim());
  const reasonValid = reason.trim().length > 0;
  const canReview = idValid && reasonValid;
  const challengeMatches =
    confirmChallenge.trim().toLowerCase() === deviceId.trim().toLowerCase();

  const errors = useMemo(() => {
    const e: { deviceId?: string; reason?: string } = {};
    if (deviceId.trim() && !idValid) {
      e.deviceId = 'Must be a canonical (lowercase) UUID v4, e.g. 00000000-0000-4000-8000-000000000000';
    }
    if (reason.trim() && !reasonValid) {
      e.reason = 'An audit reason is required for regulatory and forensic compliance';
    }
    return e;
  }, [deviceId, idValid, reason, reasonValid]);

  const handleStartReview = () => {
    if (!canReview) return;
    setStage('confirm');
    setConfirmChallenge('');
  };

  const handleExecuteRevocation = async () => {
    if (!challengeMatches) return;
    try {
      const res = await run({ device_id: deviceId.trim(), reason: reason.trim() });
      setResult(res);
      setStage('done');
    } catch {
      // error surfaced via feedback
    }
  };

  const handleReset = () => {
    setDeviceId('');
    setReason('');
    setConfirmChallenge('');
    setResult(null);
    setStage('identify');
  };

  return (
    <section
      id="revocation"
      className="tactical-panel tactical-panel--danger"
      aria-label="Device Certificate Revocation Terminal"
    >
      <div className="tactical-panel__head">
        <div className="tactical-panel__title-group">
          <div className="tactical-panel__icon-box" style={{ color: 'var(--danger)' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </div>
          <div>
            <h2 className="tactical-panel__title">Device Revocation</h2>
            <div className="tactical-panel__sub">CRL Certificate Authority Terminal</div>
          </div>
        </div>
        <Tag tone="danger" dot>CRITICAL ACTION</Tag>
      </div>

      <div className="tactical-panel__body">
        {/* Step Indicator */}
        <div className="control-stepper">
          <div className={`step-dot is-danger ${stage === 'identify' ? 'is-active' : ''}`}>
            <span className="step-dot__pill">1</span>
            <span>Identify</span>
          </div>
          <div className="step-line" />
          <div className={`step-dot is-danger ${stage === 'confirm' ? 'is-active' : ''}`}>
            <span className="step-dot__pill">2</span>
            <span>Challenge</span>
          </div>
          <div className="step-line" />
          <div className={`step-dot is-danger ${stage === 'done' ? 'is-active' : ''}`}>
            <span className="step-dot__pill">3</span>
            <span>Invalidated</span>
          </div>
        </div>

        {stage === 'done' && result ? (
          <div className="revocation-receipt" role="status">
            <div className="receipt-header">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--success)" strokeWidth="3">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              <span className="receipt-title">Certificate Invalidation Confirmed</span>
            </div>

            <div className="receipt-grid">
              <div className="receipt-entry">
                <span className="receipt-k">Revoked Device ID:</span>
                <span className="receipt-v mono">{result.device_id}</span>
              </div>
              <div className="receipt-entry">
                <span className="receipt-k">Certificate Serial:</span>
                <span className="receipt-v mono">{result.serial_number}</span>
              </div>
              <div className="receipt-entry">
                <span className="receipt-k">Revocation Epoch:</span>
                <span className="receipt-v mono">{formatEpoch(result.revoked_at)}</span>
              </div>
              <div className="receipt-entry">
                <span className="receipt-k">Audit Reason:</span>
                <span className="receipt-v mono" style={{ fontSize: 'var(--text-3xs)' }}>{result.reason}</span>
              </div>
            </div>

            <div style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
              Serial <span className="mono">{result.serial_number}</span> has been written to the Certificate Revocation List (CRL) and will broadcast to all offline sync terminals immediately.
            </div>

            <Button variant="quiet" size="sm" block onClick={handleReset}>
              Revoke Another Device Certificate
            </Button>
          </div>
        ) : (
          <div className="revocation-terminal">
            {stage === 'identify' && (
              <>
                <Field
                  label="Compromised Device ID"
                  htmlFor="revoke-device-id"
                  hint="Canonical 36-character UUID v4 of the target device"
                  error={errors.deviceId}
                  required
                >
                  <TextInput
                    id="revoke-device-id"
                    mono
                    placeholder="00000000-0000-4000-8000-000000000000"
                    value={deviceId}
                    invalid={!!errors.deviceId}
                    disabled={pending}
                    onChange={(e) => setDeviceId(e.target.value)}
                  />
                </Field>

                <Field
                  label="Forensic Reason &amp; Audit Reference"
                  htmlFor="revoke-reason-input"
                  hint="Permanent reason logged in the immutable security ledger"
                  error={errors.reason}
                  required
                >
                  <textarea
                    id="revoke-reason-input"
                    className="textarea"
                    placeholder="e.g. Lost device reported by cardholder, ticket #4819"
                    value={reason}
                    disabled={pending}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </Field>

                {/* Reason Presets */}
                <div className="reason-presets-row">
                  <span className="overline" style={{ fontSize: '0.6rem' }}>Presets:</span>
                  {AUDIT_REASON_PRESETS.map((p) => (
                    <button
                      key={p}
                      type="button"
                      className="preset-chip"
                      onClick={() => setReason(p)}
                    >
                      {p.split(' ')[0]} {p.split(' ')[1]}
                    </button>
                  ))}
                </div>

                <div className="control-actions-row">
                  <span style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
                    Destructive cryptographic revocation
                  </span>
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={!canReview}
                    onClick={handleStartReview}
                  >
                    Review Revocation →
                  </Button>
                </div>
              </>
            )}

            {stage === 'confirm' && (
              <div className="confirmation-review-box" style={{ borderColor: 'var(--danger-border)', backgroundColor: 'oklch(96.6% 0.03 27)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span className="overline" style={{ color: 'var(--danger)' }}>Confirm Destructive Action</span>
                  <Tag tone="danger">IRREVERSIBLE</Tag>
                </div>

                <div className="review-list">
                  <div className="review-item">
                    <span className="review-item__k">Target Device:</span>
                    <span className="review-item__v mono">{shortId(deviceId)}</span>
                  </div>
                  <div className="review-item">
                    <span className="review-item__k">Reason:</span>
                    <span className="review-item__v" style={{ fontSize: 'var(--text-3xs)' }}>{reason}</span>
                  </div>
                </div>

                <Field
                  label="Type or Paste Device ID to Confirm"
                  htmlFor="revoke-challenge-match"
                  hint="Type the exact UUID v4 above to unlock revocation"
                >
                  <TextInput
                    id="revoke-challenge-match"
                    mono
                    placeholder="paste device id here"
                    value={confirmChallenge}
                    disabled={pending}
                    onChange={(e) => setConfirmChallenge(e.target.value)}
                  />
                </Field>

                <div style={{ display: 'flex', gap: 'var(--sp-2)', justifyContent: 'flex-end' }}>
                  <button
                    type="button"
                    className="preset-chip"
                    onClick={() => setConfirmChallenge(deviceId)}
                  >
                    Autofill Match
                  </button>
                </div>

                <div className="control-actions-row">
                  <Button variant="ghost" size="sm" onClick={() => setStage('identify')} disabled={pending}>
                    ← Back
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    loading={pending}
                    disabled={!challengeMatches}
                    onClick={() => void handleExecuteRevocation()}
                  >
                    Revoke Certificate Immediately
                  </Button>
                </div>
              </div>
            )}

            {error && (
              <Feedback
                tone="danger"
                live="assertive"
                code={error instanceof ApiError ? `HTTP_${error.status}` : 'REVOKE_ERR'}
              >
                {error.message}
              </Feedback>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
