import { useMemo, useState } from 'react';
import { ApiError, toggleDisaster } from '../lib/api';
import type { DisasterEvent } from '../lib/types';
import { formatEpoch, formatPaise } from '../lib/format';
import { useMutation } from '../hooks/useAsync';
import { Button } from './ui/Button';
import { Feedback } from './ui/Feedback';
import { Field } from './ui/Field';
import { Select } from './ui/Select';
import { TextInput } from './ui/TextInput';
import { Toggle } from './ui/Toggle';
import { Tag } from './ui/Tag';

const DISASTER_TYPES = [
  { value: 'flood', label: 'Flood / Severe Inundation' },
  { value: 'earthquake', label: 'Earthquake / Structural Seismic' },
  { value: 'network_outage', label: 'Grid / Telecom Mesh Blackout' },
  { value: 'cyclone', label: 'Cyclone / High Storm Surge' },
];

const emptyForm = { region: '', type: 'flood', capInr: '', essentialOnly: false };

type DisasterStep = 'idle' | 'review' | 'broadcasting';

export interface DisasterControlProps {
  onStateChange?: (isActive: boolean) => void;
}

export function DisasterControl({ onStateChange }: DisasterControlProps) {
  const [form, setForm] = useState(emptyForm);
  const [enabled, setEnabled] = useState(false);
  const [step, setStep] = useState<DisasterStep>('idle');
  // Local knowledge tracking since backend has no GET endpoint
  const [lastKnown, setLastKnown] = useState<DisasterEvent | null>(null);

  const { run, pending, error } = useMutation(toggleDisaster);

  // A field the operator has not reached yet is not "wrong". Validity gates the
  // action; visibility waits until the field has been touched, so the panel does
  // not greet the operator in red.
  const [touchedRegion, setTouchedRegion] = useState(false);

  const errors = useMemo(() => {
    const e: { region?: string; type?: string; capInr?: string } = {};
    if (!form.region.trim()) e.region = 'Geographic region name or code is required';
    if (!form.type.trim()) e.type = 'Disaster classification is required';
    if (
      form.capInr.trim() &&
      (!/^\d+(\.\d{1,2})?$/.test(form.capInr.trim()) || Number(form.capInr) <= 0)
    ) {
      e.capInr = 'Higher cap must be a valid positive rupee amount, e.g. 5000 or 2500.50';
    }
    return e;
  }, [form]);

  const hasErrors = Object.keys(errors).length > 0;
  const shownRegionError = touchedRegion ? errors.region : undefined;
  const capPaise = form.capInr.trim()
    ? String(Math.round(Number(form.capInr) * 100))
    : null;

  const handleStartReview = () => {
    // Attempting to advance counts as reaching every field: surface what blocks it.
    setTouchedRegion(true);
    if (hasErrors) return;
    setStep('review');
  };

  const submitToggle = async (nextEnabled: boolean) => {
    setStep('broadcasting');
    try {
      const event = await run({
        region_geo: form.region.trim(),
        type: form.type.trim(),
        enabled: nextEnabled,
        higher_cap: capPaise,
        essential_only: nextEnabled ? form.essentialOnly : undefined,
      });
      setLastKnown(event);
      setEnabled(event.enabled);
      onStateChange?.(event.enabled);
      setStep('idle');
      if (event.enabled) {
        setForm(emptyForm);
      }
    } catch {
      setStep('idle');
    }
  };

  return (
    <section
      id="disaster"
      className={`tactical-panel ${enabled ? 'tactical-panel--warning' : ''}`}
      aria-label="Regional Disaster Operating Mode Control"
    >
      <div className="tactical-panel__head">
        <div className="tactical-panel__title-group">
          <div className="tactical-panel__icon-box" style={{ color: enabled ? 'var(--warning)' : 'var(--text-muted)' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </div>
          <div>
            <h2 className="tactical-panel__title">Disaster Mode</h2>
            <div className="tactical-panel__sub">Regional Emergency Protocol</div>
          </div>
        </div>
        <div>
          {enabled ? (
            <Tag tone="warning" dot pulse>BROADCASTING</Tag>
          ) : (
            <Tag tone="neutral">STANDBY</Tag>
          )}
        </div>
      </div>

      <div className="tactical-panel__body">
        {/* Status indicator banner */}
        <div className={`disaster-status-banner ${enabled ? 'is-active' : ''}`} role="status">
          <div className="disaster-indicator-group">
            {enabled ? (
              <div className="broadcast-wave-container" aria-hidden="true">
                <span className="broadcast-bar" />
                <span className="broadcast-bar" />
                <span className="broadcast-bar" />
                <span className="broadcast-bar" />
              </div>
            ) : (
              <span className="dot dot--ok" aria-hidden="true" />
            )}
            <span className="disaster-mode-badge">
              {enabled ? 'EMERGENCY BROADCAST ACTIVE' : 'NORMAL NETWORK CONDITIONS'}
            </span>
          </div>
          <span className="mono" style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
            {enabled ? 'STATE: OVERRIDE' : 'STATE: DEFAULT'}
          </span>
        </div>

        {/* If Disaster is Active, show telemetry readout */}
        {enabled && lastKnown && (
          <div className="disaster-active-summary">
            <div className="disaster-summary-row">
              <span className="disaster-summary-label">Target Zone:</span>
              <span className="disaster-summary-val mono">{lastKnown.region_geo}</span>
            </div>
            <div className="disaster-summary-row">
              <span className="disaster-summary-label">Incident Type:</span>
              <span className="disaster-summary-val mono">{lastKnown.type}</span>
            </div>
            <div className="disaster-summary-row">
              <span className="disaster-summary-label">Emergency Cap:</span>
              <span className="disaster-summary-val mono">
                {lastKnown.higher_cap ? formatPaise(lastKnown.higher_cap) : 'DEFAULT (₹2,000)'}
              </span>
            </div>
            <div className="disaster-summary-row">
              <span className="disaster-summary-label">Essential Only:</span>
              <span className="disaster-summary-val mono">
                {lastKnown.essential_only ? 'YES (RESTRICTED)' : 'NO (STANDARD)'}
              </span>
            </div>
            <div className="disaster-summary-row" style={{ paddingTop: 'var(--sp-2)', borderTop: '1px solid var(--warning-border)' }}>
              <span className="disaster-summary-label">Activated:</span>
              <span className="disaster-summary-val mono">{formatEpoch(lastKnown.updated_at)}</span>
            </div>
            <div style={{ paddingTop: 'var(--sp-2)' }}>
              <Button
                variant="danger"
                size="sm"
                block
                loading={pending}
                onClick={() => void submitToggle(false)}
              >
                Deactivate Disaster Mode
              </Button>
            </div>
          </div>
        )}

        {!enabled && lastKnown && (
          <Feedback tone="neutral">
            Prior event in zone <span className="mono">{lastKnown.region_geo}</span> terminated at{' '}
            <span className="mono">{formatEpoch(lastKnown.updated_at)}</span>. State is tracked locally on this console.
          </Feedback>
        )}

        {/* Configuration form (shown when not active) */}
        {!enabled && (
          <div className="disaster-form" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3-5)' }}>
            <div className="control-stepper">
              <div className={`step-dot is-warning ${step === 'idle' ? 'is-active' : ''}`}>
                <span className="step-dot__pill">1</span>
                <span>Configure</span>
              </div>
              <div className="step-line" />
              <div className={`step-dot is-warning ${step === 'review' ? 'is-active' : ''}`}>
                <span className="step-dot__pill">2</span>
                <span>Authorize</span>
              </div>
              <div className="step-line" />
              <div className={`step-dot is-warning ${step === 'broadcasting' ? 'is-active' : ''}`}>
                <span className="step-dot__pill">3</span>
                <span>Broadcast</span>
              </div>
            </div>

            {step === 'idle' && (
              <>
                <Field
                  label="Target Region"
                  htmlFor="disaster-region"
                  hint="Regional geographic zone code (e.g. chennai-north, mumbai-harbour)"
                  error={shownRegionError}
                  required
                >
                  <TextInput
                    id="disaster-region"
                    mono
                    placeholder="e.g. chennai-north"
                    value={form.region}
                    invalid={!!shownRegionError}
                    onBlur={() => setTouchedRegion(true)}
                    onChange={(e) => setForm({ ...form, region: e.target.value })}
                  />
                </Field>

                <Field label="Disaster Classification" htmlFor="disaster-type" required>
                  <Select
                    id="disaster-type"
                    value={form.type}
                    onChange={(e) => setForm({ ...form, type: e.target.value })}
                  >
                    {DISASTER_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </Select>
                </Field>

                <Field
                  label="Emergency Spend Cap (₹)"
                  htmlFor="disaster-cap"
                  hint={
                    capPaise
                      ? `Computed payload: ${capPaise} integer paise (${formatPaise(capPaise)})`
                      : 'Optional elevated offline spending limit for humanitarian transactions'
                  }
                  error={errors.capInr}
                >
                  <TextInput
                    id="disaster-cap"
                    mono
                    inputMode="decimal"
                    placeholder="e.g. 5000"
                    value={form.capInr}
                    invalid={!!errors.capInr}
                    onChange={(e) => setForm({ ...form, capInr: e.target.value })}
                  />
                </Field>

                <Toggle
                  id="disaster-essential"
                  checked={form.essentialOnly}
                  onChange={(next) => setForm({ ...form, essentialOnly: next })}
                  label="Enforce essential-only rations &amp; medical commodities"
                  hint="Restricts non-emergency commercial transactions until disaster mode clears"
                />

                <div className="control-actions-row">
                  <span style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
                    Requires operator authorization review
                  </span>
                  <Button
                    variant="warning"
                    size="sm"
                    disabled={hasErrors}
                    onClick={handleStartReview}
                  >
                    Review Activation →
                  </Button>
                </div>
              </>
            )}

            {step === 'review' && (
              <div className="confirmation-review-box">
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span className="overline" style={{ color: 'var(--warning)' }}>Emergency Protocol Pre-Flight</span>
                  <Tag tone="warning">STAGE 2 / 3</Tag>
                </div>

                <div className="review-list">
                  <div className="review-item">
                    <span className="review-item__k">Target Region:</span>
                    <span className="review-item__v">{form.region || '—'}</span>
                  </div>
                  <div className="review-item">
                    <span className="review-item__k">Disaster Event:</span>
                    <span className="review-item__v">{form.type || '—'}</span>
                  </div>
                  <div className="review-item">
                    <span className="review-item__k">Offline Spend Limit:</span>
                    <span className="review-item__v">
                      {capPaise ? `${formatPaise(capPaise)} (${capPaise} paise)` : 'Default (₹2,000)'}
                    </span>
                  </div>
                  <div className="review-item">
                    <span className="review-item__k">Essential Commodities Only:</span>
                    <span className="review-item__v">{form.essentialOnly ? 'ENFORCED' : 'DISABLED'}</span>
                  </div>
                </div>

                <div style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-secondary)', lineHeight: 'var(--leading-snug)' }}>
                  Activating will broadcast this state across all mesh synchronization points. All peer-to-peer devices entering this region will ingest the elevated cap on next sync.
                </div>

                <div className="control-actions-row">
                  <Button variant="ghost" size="sm" onClick={() => setStep('idle')} disabled={pending}>
                    ← Modify
                  </Button>
                  <Button
                    variant="warning"
                    size="sm"
                    loading={pending}
                    onClick={() => void submitToggle(true)}
                  >
                    Broadcast Emergency Mode
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

        {error && (
          <Feedback tone="danger" live="assertive" code={error instanceof ApiError ? `HTTP_${error.status}` : 'DISASTER_ERR'}>
            {error.message}
          </Feedback>
        )}
      </div>
    </section>
  );
}
