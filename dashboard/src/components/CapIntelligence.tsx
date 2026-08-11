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

            {/* No fabricated signal decomposition here: with no model deployed there are no
                signals to show, and inventing plausible-looking bars would be exactly the kind
                of fabrication this console refuses to do. */}

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
              <Tag tone="accent">LIVE RECOMMENDATION</Tag>
            </div>

            {/* Every bar below is a value the model returned. Nothing here is illustrative. */}
            {data.signals && data.signals.length > 0 && (
              <div className="signal-decomposition">
                <span className="overline" style={{ color: 'var(--text-muted)' }}>
                  Offline Risk Signal Decomposition
                </span>

                {data.signals.map((signal) => (
                  <div className="signal-row" key={signal.key} title={signal.detail}>
                    <div className="signal-row__head">
                      <span className="signal-row__label">{signal.label}</span>
                      <span className="signal-row__score">
                        {signal.score.toFixed(2)} × {signal.weight.toFixed(2)}
                      </span>
                    </div>
                    <div className="signal-meter">
                      <div
                        className="signal-meter__fill"
                        style={{ width: `${Math.round(signal.score * 100)}%` }}
                      />
                    </div>
                    <div className="signal-row__detail">{signal.detail}</div>
                  </div>
                ))}
              </div>
            )}

            {data.balance_capped && (
              <Feedback tone="warn" code="BALANCE_BOUND">
                The account's real balance, not the risk score, is the binding constraint on this cap.
              </Feedback>
            )}

            {data.disaster_override && (
              <Feedback tone="warn" code="DISASTER_OVERRIDE">
                Raised to <span className="mono">{formatPaise(data.disaster_override.higher_cap)}</span> by the
                active disaster event in <span className="mono">{data.disaster_override.region_geo}</span> —
                an operator-authorised exception, not a model output.
              </Feedback>
            )}

            <div className="cap-model-footer">
              <span>
                Integer paise <span className="mono">{data.recommended_cap}</span>
                {typeof data.confidence === 'number' && (
                  <> · confidence <span className="mono">{data.confidence.toFixed(3)}</span></>
                )}
              </span>
              {data.model_version && <span className="mono">{data.model_version}</span>}
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
