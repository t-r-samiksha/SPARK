import { useEffect, useState } from 'react';
import { API_BASE, getHealth, hasAdminKey } from '../lib/api';
import { Tag } from './ui/Tag';

export type SystemPosture = 'OPERATIONAL' | 'INCIDENT_ACTIVE' | 'DISASTER_BROADCAST' | 'DEGRADED';

interface StatusBarProps {
  posture?: SystemPosture;
  incidentCount?: number;
  isDisasterActive?: boolean;
}

function useUtcClock(): { time: string; date: string } {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(t);
  }, []);

  const pad = (v: number) => String(v).padStart(2, '0');
  const time = `${pad(now.getUTCHours())}:${pad(now.getUTCMinutes())}:${pad(now.getUTCSeconds())} UTC`;
  const date = `${now.getUTCFullYear()}-${pad(now.getUTCMonth() + 1)}-${pad(now.getUTCDate())}`;
  
  return { time, date };
}

export function StatusBar({
  posture = 'OPERATIONAL',
  incidentCount = 0,
  isDisasterActive = false,
}: StatusBarProps) {
  const [conn, setConn] = useState<'checking' | 'ok' | 'down'>('checking');
  const [latencyMs, setLatencyMs] = useState<number | null>(null);
  const clock = useUtcClock();
  const env = import.meta.env.VITE_ENV ?? 'staging';

  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      const start = performance.now();
      try {
        await getHealth();
        const duration = Math.round(performance.now() - start);
        if (!cancelled) {
          setConn('ok');
          setLatencyMs(duration);
        }
      } catch {
        if (!cancelled) {
          setConn('down');
          setLatencyMs(null);
        }
      }
    };

    check();
    const t = window.setInterval(check, 15000);
    return () => {
      cancelled = true;
      window.clearInterval(t);
    };
  }, []);

  // Compute live posture based on system inputs
  const currentPosture: SystemPosture = isDisasterActive
    ? 'DISASTER_BROADCAST'
    : incidentCount > 0
      ? 'INCIDENT_ACTIVE'
      : conn === 'down'
        ? 'DEGRADED'
        : posture;

  return (
    <header className="tactical-header" role="banner">
      <div className="tactical-header__left">
        <a href="#main" className="tactical-brand" aria-label="SPARK Operations Console">
          <div className="tactical-brand__logo" aria-hidden="true">
            <span className="tactical-brand__icon">⚡</span>
          </div>
          <div className="tactical-brand__meta">
            <div className="tactical-brand__title">
              SPARK
              <span className="tactical-brand__badge">SOC · OPS</span>
            </div>
            <div className="tactical-brand__desc">Offline Payment Defense Console</div>
          </div>
        </a>
      </div>

      <div className="tactical-header__center" aria-label="Live Telemetry Nodes">
        <div className="telemetry-node" title="Backend Health & REST API Connectivity">
          <span
            className={`status-beacon status-beacon--${conn === 'ok' ? 'ok' : conn === 'down' ? 'danger' : 'warn'}`}
            aria-hidden="true"
          />
          <span className="overline">Mesh Node:</span>
          <span className="telemetry-node__pill">
            <span className="mono">{conn === 'ok' ? 'ONLINE' : conn === 'down' ? 'UNREACHABLE' : 'PROBING…'}</span>
            {latencyMs !== null && <span className="mono text-muted">{latencyMs}ms</span>}
          </span>
        </div>

        <div className="telemetry-node" title="Admin Shared Secret State">
          <span className="overline">Key Status:</span>
          <span className="telemetry-node__pill">
            <span className={`dot ${hasAdminKey() ? 'dot--ok' : 'dot--warn'}`} aria-hidden="true" />
            <span className="mono">{hasAdminKey() ? 'X-ADMIN-KEY ACTIVE' : 'KEY UNSET'}</span>
          </span>
        </div>

        <div className="telemetry-node" title="Deployment Environment">
          <span className="overline">Env:</span>
          <Tag tone={env === 'prod' ? 'danger' : 'info'}>{env}</Tag>
        </div>

        <div className="telemetry-node" title="API Base URL">
          <span className="overline">Gateway:</span>
          <span className="telemetry-node__pill mono">{API_BASE.replace(/^https?:\/\//, '')}</span>
        </div>
      </div>

      <div className="tactical-header__right">
        <div className="status-indicator">
          <span className="overline">Posture:</span>
          {currentPosture === 'OPERATIONAL' && (
            <Tag tone="accent" dot pulse>OPERATIONAL</Tag>
          )}
          {currentPosture === 'INCIDENT_ACTIVE' && (
            <Tag tone="danger" dot pulse>INCIDENT ALERT ({incidentCount})</Tag>
          )}
          {currentPosture === 'DISASTER_BROADCAST' && (
            <Tag tone="warning" dot pulse>DISASTER BROADCAST</Tag>
          )}
          {currentPosture === 'DEGRADED' && (
            <Tag tone="warning" dot>DEGRADED SYNC</Tag>
          )}
        </div>

        <div className="clock-telemetry" title="Universal Coordinated Time" aria-label={`UTC Atomic Time: ${clock.time}`}>
          <span className="mono">{clock.time}</span>
        </div>
      </div>
    </header>
  );
}
