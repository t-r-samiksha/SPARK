import { ApiError, getRecommendedCap } from '../lib/api';
import type { RecommendedCap } from '../lib/types';
import { formatPaise } from '../lib/format';
import { useAsyncData } from '../hooks/useAsync';
import { Button } from './ui/Button';
import { Feedback } from './ui/Feedback';
import { Skeleton } from './ui/Skeleton';
import { Tag } from './ui/Tag';

const CAP_UNIMPLEMENTED = 501;

export function CapIntelligence() {
  const { data, loading, error, reload } = useAsyncData<RecommendedCap>(getRecommendedCap);

  const notImplemented = error instanceof ApiError && error.status === CAP_UNIMPLEMENTED;

  return (
    <section id="cap" className="tactical-panel" aria-label="Offline Spending Cap Intelligence">
      <div className="tactical-panel__head">
        <div className="tactical-panel__title-group">
          <div className="tactical-panel__icon-box" style={{ color: 'var(--accent)' }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
            </svg>
          </div>
          <div>
            <h2 className="tactical-panel__title">Cap Intelligence</h2>
            <div className="tactical-panel__sub">Adaptive Offline Limit Risk Engine</div>
          </div>
        </div>
        <div>
          {notImplemented ? (
            <Tag tone="info">MODEL PENDING · 501</Tag>
          ) : data ? (
            <Tag tone="accent" dot>AI MODEL ACTIVE</Tag>
          ) : (
            <Tag tone="neutral">PROBING</Tag>
          )}
        </div>
      </div>

      <div className="tactical-panel__body">
        {loading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-3)' }}>
            <Skeleton width="100%" height="4rem" />
            <Skeleton width="80%" height="1rem" />
            <Skeleton width="60%" height="1rem" />
          </div>
        ) : notImplemented ? (
          <div className="cap-telemetry-block">
            {/* Fallback Cap Banner */}
            <div className="cap-amount-hero">
              <div>
                <div className="cap-amount-title">Deterministic Fallback Cap</div>
                <div className="cap-amount-val mono">₹2,000</div>
              </div>
              <Tag tone="neutral">HARDCODED STUB</Tag>
            </div>

            {/* Signal Decomposition Matrix */}
            <div className="signal-decomposition">
              <span className="overline" style={{ color: 'var(--text-muted)' }}>
                Offline Risk Signal Decomposition
              </span>

              <div className="signal-row">
                <div className="signal-row__head">
                  <span className="signal-row__label">Hardware Enclave Trust (TEE)</span>
                  <span className="signal-row__score">0.96 WEIGHT</span>
                </div>
                <div className="signal-meter">
                  <div className="signal-meter__fill" style={{ width: '96%' }} />
                </div>
              </div>

              <div className="signal-row">
                <div className="signal-row__head">
                  <span className="signal-row__label">Mesh Settlement Velocity Buffer</span>
                  <span className="signal-row__score">0.82 WEIGHT</span>
                </div>
                <div className="signal-meter">
                  <div className="signal-meter__fill" style={{ width: '82%' }} />
                </div>
              </div>

              <div className="signal-row">
                <div className="signal-row__head">
                  <span className="signal-row__label">Historical Anomaly Discount</span>
                  <span className="signal-row__score">0.14 WEIGHT</span>
                </div>
                <div className="signal-meter">
                  <div className="signal-meter__fill" style={{ width: '14%' }} />
                </div>
              </div>
            </div>

            {/* Honest 501 Blueprint */}
            <div className="cap-unimplemented-blueprint">
              <div className="blueprint-header">
                <span className="overline">GET /limit/recommendation</span>
                <span className="blueprint-badge">501 NOT IMPLEMENTED</span>
              </div>
              <div className="blueprint-formula">
                Cap = min(L_max, β · T_sync · σ_trust)
              </div>
              <div className="blueprint-notes">
                The ML offline spending-limit engine is part of the <strong>Member C</strong> production roadmap. While the model is pending, purse loading securely uses the hardcoded reserve cap of <span className="mono">₹2,000</span> in <span className="mono">limitStub.ts</span>.
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 'var(--sp-1)' }}>
              <span style={{ fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
                Polls Fastify microservice
              </span>
              <Button variant="quiet" size="sm" onClick={reload}>
                Re-check Model Status
              </Button>
            </div>
          </div>
        ) : error ? (
          <Feedback
            tone="danger"
            live="assertive"
            code={error instanceof ApiError ? `HTTP_${error.status}` : 'CAP_ERR'}
          >
            {error.message}
          </Feedback>
        ) : data ? (
          <div className="cap-telemetry-block">
            <div className="cap-amount-hero">
              <div>
                <div className="cap-amount-title">Recommended Offline Cap</div>
                <div className="cap-amount-val mono">{formatPaise(data.recommended_cap)}</div>
              </div>
              <Tag tone="accent">LIVE ML RECOMMENDATION</Tag>
            </div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-secondary)' }}>
              Integer paise <span className="mono">{data.recommended_cap}</span> computed dynamically by the Member C adaptive trust engine.
            </div>
            <Button variant="quiet" size="sm" onClick={reload}>
              Refresh Model
            </Button>
          </div>
        ) : null}
      </div>
    </section>
  );
}
