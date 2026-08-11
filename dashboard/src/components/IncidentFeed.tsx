import { useEffect, useMemo, useState } from 'react';
import { ApiError, getIncidents, hasAdminKey } from '../lib/api';
import type { DoubleSpendIncident, Incident, IncidentType } from '../lib/types';
import { formatEpoch, formatRelative, shortId } from '../lib/format';
import { useAsyncData } from '../hooks/useAsync';
import { Button } from './ui/Button';
import { Feedback } from './ui/Feedback';
import { Skeleton } from './ui/Skeleton';
import { Tag } from './ui/Tag';

export interface IncidentFeedProps {
  onSelectRevokeDevice?: (deviceId: string) => void;
  onIncidentsLoaded?: (count: number) => void;
}

const TABS: { value: IncidentType; label: string; countKey: 'all' | 'double_spend' | 'fraud_flag' }[] = [
  { value: 'all', label: 'All Incidents', countKey: 'all' },
  { value: 'double_spend', label: 'Double-Spend Breaches', countKey: 'double_spend' },
  { value: 'fraud_flag', label: 'Fraud Flags (AI)', countKey: 'fraud_flag' },
];

function isDoubleSpend(i: Incident): i is DoubleSpendIncident {
  return i.type === 'double_spend';
}

function CopyBadge({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation();
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    });
  };

  return (
    <button
      type="button"
      className={`copy-badge-btn ${copied ? 'is-copied' : ''}`}
      onClick={handleCopy}
      title={`Copy ${label} to clipboard`}
      aria-label={`Copy ${label}`}
    >
      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
        {copied ? (
          <path d="M20 6L9 17l-5-5" />
        ) : (
          <>
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </>
        )}
      </svg>
      <span>{copied ? 'copied' : label}</span>
    </button>
  );
}

function RadarScanningEmpty({ filter, searchQuery }: { filter: IncidentType; searchQuery: string }) {
  if (searchQuery.trim()) {
    return (
      <div className="feed-radar-empty" role="status">
        <div className="feed-empty-title">No matching incidents found</div>
        <div className="feed-empty-desc">
          No security events match query <span className="mono">"{searchQuery}"</span>. Try searching by canonical UUID or token identifier.
        </div>
      </div>
    );
  }

  if (filter === 'fraud_flag') {
    return (
      <div className="feed-radar-empty" role="status">
        <div className="radar-scope" aria-hidden="true">
          <div className="radar-scope__beam" />
          <div className="radar-scope__cross" />
          <div className="radar-scope__cross radar-scope__cross--vert" />
        </div>
        <Tag tone="accent" dot>NO BEHAVIOURAL FLAGS</Tag>
        <div className="feed-empty-title">Nothing currently flagged</div>
        <div className="feed-empty-desc">
          The intelligence service scanned the fleet and found no device whose behaviour warrants
          review. A flag needs corroborating signals, so quiet here means quiet — not that nothing
          was checked.
        </div>
      </div>
    );
  }

  return (
    <div className="feed-radar-empty" role="status">
      <div className="radar-scope" aria-hidden="true">
        <div className="radar-scope__beam" />
        <div className="radar-scope__cross" />
        <div className="radar-scope__cross radar-scope__cross--vert" />
      </div>
      <Tag tone="accent" dot pulse>SETTLEMENT MESH CLEAN</Tag>
      <div className="feed-empty-title">
        {filter === 'double_spend' ? 'No double-spend incidents detected' : 'No security incidents recorded'}
      </div>
      <div className="feed-empty-desc">
        The settlement engine reports zero slot collisions across the offline network mesh. Incidents will surface here with high priority the instant conflicting offline signatures are submitted.
      </div>
    </div>
  );
}

export function IncidentFeed({ onSelectRevokeDevice, onIncidentsLoaded }: IncidentFeedProps) {
  const [filter, setFilter] = useState<IncidentType>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = () =>
    getIncidents(filter).then((r) => {
      setLastUpdated(new Date());
      return r.incidents;
    });

  const { data, loading, error, reload } = useAsyncData<Incident[]>(load, [filter]);

  // Sync count to parent telemetry
  useEffect(() => {
    if (data) {
      onIncidentsLoaded?.(data.length);
    }
  }, [data, onIncidentsLoaded]);

  const counts = useMemo(() => {
    const all = data ?? [];
    return {
      all: all.length,
      double_spend: all.filter(isDoubleSpend).length,
      fraud_flag: all.filter((i) => i.type === 'fraud_flag').length,
    };
  }, [data]);

  // Filtered by search term (device_id, token_id, tx_id, id)
  const filteredData = useMemo(() => {
    if (!data) return [];
    if (!searchQuery.trim()) return data;
    const q = searchQuery.toLowerCase().trim();
    return data.filter((inc) => {
      if (isDoubleSpend(inc)) {
        return (
          inc.id.toLowerCase().includes(q) ||
          inc.device_id.toLowerCase().includes(q) ||
          inc.token_id.toLowerCase().includes(q) ||
          inc.tx_id_a.toLowerCase().includes(q) ||
          inc.tx_id_b.toLowerCase().includes(q)
        );
      }
      return (
        inc.device_id?.toLowerCase().includes(q) ||
        inc.id?.toLowerCase().includes(q) ||
        inc.reasons?.some((r) => r.label.toLowerCase().includes(q)) ||
        false
      );
    });
  }, [data, searchQuery]);

  const keyConfigured = hasAdminKey();

  return (
    <section id="incidents" className="feed-container" aria-label="Security Incidents Feed">
      <div className="feed-header">
        <div className="feed-title-block">
          <div className="feed-title-row">
            <h1 className="feed-main-title">Security Incidents</h1>
            {counts.double_spend > 0 ? (
              <Tag tone="danger" dot pulse>{counts.double_spend} CRITICAL BREACHES</Tag>
            ) : (
              <Tag tone="accent" dot>MESH SECURE</Tag>
            )}
          </div>
          <p className="feed-subtitle">Settlement conflict telemetry &amp; offline signature anomalies</p>
        </div>

        <div className="feed-controls">
          <div className="segmented-filter" role="group" aria-label="Filter incidents by security type">
            {TABS.map((t) => (
              <button
                key={t.value}
                type="button"
                aria-pressed={filter === t.value}
                className={`segmented-filter__btn ${filter === t.value ? 'is-active' : ''}`}
                onClick={() => {
                  setFilter(t.value);
                  setExpandedId(null);
                }}
              >
                {t.label}
                <span className="segmented-filter__count">
                  {loading ? '·' : counts[t.countKey]}
                </span>
              </button>
            ))}
          </div>

          <div className="feed-search-box">
            <span className="feed-search-icon" aria-hidden="true">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
            </span>
            <input
              type="text"
              className="input input--mono feed-search-input"
              placeholder="Search UUID / Token / Tx..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Filter incidents by ID or token"
            />
          </div>

          <Button
            variant="quiet"
            size="sm"
            onClick={reload}
            disabled={loading}
            icon={
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
              </svg>
            }
          >
            {loading ? 'Syncing…' : 'Sync Feed'}
          </Button>
        </div>
      </div>

      {!keyConfigured && (
        <Feedback tone="warn" code="AUTH_CONFIG_ALERT">
          No admin key found. Ensure <span className="mono">VITE_ADMIN_KEY</span> is populated in <span className="mono">.env.local</span> to authorize admin actions.
        </Feedback>
      )}

      {error && (
        <Feedback
          tone="danger"
          live="assertive"
          code={error instanceof ApiError ? `HTTP_${error.status}` : 'CONNECTION_FAILURE'}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <span>{error.message}</span>
            <Button variant="ghost" size="xs" onClick={reload}>Retry</Button>
          </div>
        </Feedback>
      )}

      <div className="incident-feed-panel">
        {/* Column headings describe rows. With none to describe — an error, or an
            empty mesh — they are suppressed rather than left stranded. */}
        {!error && (
          <div className="incident-table-header" aria-hidden="true">
            <span>Incident Type</span>
            <span>Compromised Device</span>
            <span>Purse Token</span>
            <span>Conflicting Slot (Tx A / B)</span>
            <span>Detected Time</span>
            <span style={{ textAlign: 'right' }}>Inspect</span>
          </div>
        )}

        {error ? (
          <div className="feed-radar-empty" role="status">
            <div className="feed-empty-icon feed-empty-icon--danger" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <path d="M12 9v4" />
                <path d="M12 17h.01" />
                <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              </svg>
            </div>
            <div className="feed-empty-title">Incident feed unreachable</div>
            <div className="feed-empty-desc">
              The console cannot read{' '}
              <span className="mono">/admin/incidents</span> right now, so it is showing
              nothing rather than stale or invented events. Retry once the settlement
              engine responds.
            </div>
            <Button variant="quiet" size="sm" onClick={reload} disabled={loading}>
              {loading ? 'Retrying…' : 'Retry sync'}
            </Button>
          </div>
        ) : loading ? (
          <div className="incident-list" aria-label="Loading security incidents">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="incident-row" style={{ animationDelay: `${i * 50}ms` }}>
                <Skeleton width="5.5rem" height="1.5rem" />
                <Skeleton width="80%" height="1.1rem" />
                <Skeleton width="70%" height="1.1rem" />
                <Skeleton width="90%" height="1.1rem" />
                <Skeleton width="5rem" height="1.1rem" />
                <Skeleton width="1.75rem" height="1.75rem" />
              </div>
            ))}
          </div>
        ) : filteredData.length === 0 ? (
          <RadarScanningEmpty filter={filter} searchQuery={searchQuery} />
        ) : (
          <div className="incident-list">
            {filteredData.map((incident, idx) => (
              <IncidentRowItem
                key={isDoubleSpend(incident) ? incident.id : (incident.id ?? `fraud-${idx}`)}
                incident={incident}
                idx={idx}
                isExpanded={isDoubleSpend(incident) && expandedId === incident.id}
                onToggle={() => {
                  // Only double-spend incidents have a forensic inspector to open.
                  if (isDoubleSpend(incident)) {
                    setExpandedId(expandedId === incident.id ? null : incident.id);
                  }
                }}
                onSelectRevoke={() => {
                  // Both incident kinds carry a device the operator may want to act on; the
                  // revocation terminal still requires its own explicit confirmation.
                  const deviceId = isDoubleSpend(incident) ? incident.device_id : incident.device_id;
                  if (deviceId) {
                    onSelectRevokeDevice?.(deviceId);
                  }
                }}
              />
            ))}
          </div>
        )}
      </div>

      {lastUpdated && (
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-3xs)', color: 'var(--text-muted)' }}>
          <span>Telemetry Source: Fastify Settlement Engine</span>
          <span>Last Sync: <span className="mono">{formatEpoch(Math.floor(lastUpdated.getTime() / 1000))}</span></span>
        </div>
      )}
    </section>
  );
}

function IncidentRowItem({
  incident,
  idx,
  isExpanded,
  onToggle,
  onSelectRevoke,
}: {
  incident: Incident;
  idx: number;
  isExpanded: boolean;
  onToggle: () => void;
  onSelectRevoke: () => void;
}) {
  if (!isDoubleSpend(incident)) {
    // Behavioural flag from the intelligence service. Unlike a double-spend it is a suspicion,
    // not a proof, so the row states the score and the reasons and offers review — never an
    // automatic action.
    const score = incident.score ?? 0;
    const tone = score >= 0.85 ? 'danger' : 'warning';

    return (
      <div className="incident-row-wrapper">
        <div className="incident-row" style={{ animationDelay: `${idx * 40}ms` }}>
          <div className="incident-cell incident-cell--type">
            <Tag tone={tone} dot>FRAUD_FLAG</Tag>
          </div>
          <div className="incident-cell incident-cell--device">
            <span className="incident-id-text">
              {incident.device_id ? shortId(incident.device_id) : 'Unattributed'}
            </span>
            <span className="incident-meta-text">
              {incident.model_version ?? 'HEURISTIC EVALUATION'}
            </span>
          </div>
          <div className="incident-cell incident-cell--token">
            <span className="mono" style={{ color: 'var(--text-primary)', fontWeight: 600 }}>
              {score.toFixed(2)}
            </span>
            <span className="incident-meta-text">SUSPICION SCORE</span>
          </div>
          <div className="incident-cell">
            {incident.reasons && incident.reasons.length > 0 ? (
              <div className="fraud-reason-list">
                {incident.reasons.map((reason) => (
                  <span className="fraud-reason" key={reason.key} title={reason.detail}>
                    {reason.label} <span className="mono">{reason.score.toFixed(2)}</span>
                  </span>
                ))}
              </div>
            ) : (
              <span className="text-muted" style={{ fontSize: 'var(--text-xs)' }}>
                No reasons reported
              </span>
            )}
          </div>
          <div className="incident-cell incident-cell--time">
            <span className="incident-time">
              {incident.detected_at ? formatRelative(incident.detected_at) : '—'}
            </span>
          </div>
          <div className="incident-actions-cell">
            {incident.device_id ? (
              <Button
                variant="quiet"
                size="xs"
                onClick={(e) => {
                  e.stopPropagation();
                  onSelectRevoke();
                }}
              >
                Review
              </Button>
            ) : (
              <span className="tag tag--neutral tag--xs">N/A</span>
            )}
          </div>
        </div>
      </div>
    );
  }

  const id = incident.id;

  return (
    <div className={`incident-row-wrapper ${isExpanded ? 'is-expanded' : ''}`}>
      <div
        className="incident-row"
        style={{ animationDelay: `${idx * 40}ms` }}
        onClick={onToggle}
        role="button"
        tabIndex={0}
        aria-expanded={isExpanded}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onToggle();
          }
        }}
      >
        <div className="incident-cell incident-cell--type">
          <Tag tone="danger" dot pulse>DOUBLE_SPEND</Tag>
        </div>

        <div className="incident-cell incident-cell--device">
          <span className="incident-id-text" title={incident.device_id}>
            {shortId(incident.device_id)}
          </span>
          <span className="incident-meta-text">DEVICE_ID</span>
        </div>

        <div className="incident-cell incident-cell--token">
          <span className="incident-id-text" title={incident.token_id}>
            {shortId(incident.token_id)}
          </span>
          <span className="incident-meta-text">PURSE_TOKEN</span>
        </div>

        <div className="incident-cell">
          <div className="tx-conflict-pill" title={`${incident.tx_id_a} vs ${incident.tx_id_b}`}>
            <span>{shortId(incident.tx_id_a)}</span>
            <span className="tx-conflict-pill__divider">⚡</span>
            <span>{shortId(incident.tx_id_b)}</span>
          </div>
          <span className="incident-meta-text">SLOT COLLISION</span>
        </div>

        <div className="incident-cell incident-cell--time">
          <span className="incident-time" title={formatEpoch(incident.detected_at)}>
            {formatRelative(incident.detected_at)}
          </span>
        </div>

        <div className="incident-actions-cell">
          <button
            type="button"
            className="expand-chevron-btn"
            aria-label={isExpanded ? 'Collapse forensic report' : 'Expand forensic report'}
            onClick={(e) => {
              e.stopPropagation();
              onToggle();
            }}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
        </div>
      </div>

      {/* Accordion Inspector Sheet */}
      <div className="incident-inspector" id={`incident-inspector-${id}`}>
        <div className="incident-inspector__inner">
          <div className="incident-inspector__content">
            <div className="forensic-note">
              <div className="forensic-note__icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                  <line x1="12" y1="9" x2="12" y2="13" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </div>
              <div>
                <strong>Cryptographic Sequence Collision Detected:</strong> Two divergent, validly-signed offline transactions claimed the exact same <span className="mono">(token, counter)</span> sequence state. The settlement engine accepted Transaction A and rejected Transaction B upon network sync.
              </div>
            </div>

            <div className="collision-diff-grid">
              <div className="collision-card">
                <div className="collision-card__header">
                  <span className="collision-card__title">Incident Reference</span>
                  <CopyBadge text={incident.id} label="copy id" />
                </div>
                <div className="collision-card__value mono">{incident.id}</div>
              </div>

              <div className="collision-card">
                <div className="collision-card__header">
                  <span className="collision-card__title">Target Device Identifier</span>
                  <CopyBadge text={incident.device_id} label="copy device" />
                </div>
                <div className="collision-card__value mono">{incident.device_id}</div>
              </div>

              <div className="collision-card collision-card--danger">
                <div className="collision-card__header">
                  <span className="collision-card__title" style={{ color: 'var(--danger)' }}>Tx A (First Settled)</span>
                  <CopyBadge text={incident.tx_id_a} label="copy tx A" />
                </div>
                <div className="collision-card__value mono">{incident.tx_id_a}</div>
              </div>

              <div className="collision-card collision-card--danger">
                <div className="collision-card__header">
                  <span className="collision-card__title" style={{ color: 'var(--danger)' }}>Tx B (Rejected Collision)</span>
                  <CopyBadge text={incident.tx_id_b} label="copy tx B" />
                </div>
                <div className="collision-card__value mono">{incident.tx_id_b}</div>
              </div>
            </div>

            <div className="inspector-actions-bar">
              <div style={{ display: 'flex', gap: 'var(--sp-4)', alignItems: 'center', fontSize: 'var(--text-xs)' }}>
                <span className="overline">Purse Token:</span>
                <span className="mono">{shortId(incident.token_id)}</span>
                <CopyBadge text={incident.token_id} label="copy token" />
                <span className="overline" style={{ marginLeft: 'var(--sp-2)' }}>Detected:</span>
                <span className="mono">{formatEpoch(incident.detected_at)}</span>
              </div>

              <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectRevoke();
                  }}
                  icon={
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <circle cx="12" cy="12" r="10" />
                      <line x1="15" y1="9" x2="9" y2="15" />
                      <line x1="9" y1="9" x2="15" y2="15" />
                    </svg>
                  }
                >
                  Initiate Device Revocation
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
