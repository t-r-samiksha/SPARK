import { useEffect, useState } from 'react';

export interface RailSection {
  id: string;
  index: string;
  label: string;
  keyHint: string;
}

const SECTIONS: RailSection[] = [
  { id: 'incidents', index: '01', label: 'Incident Feed', keyHint: '1' },
  { id: 'disaster', index: '02', label: 'Disaster Mode', keyHint: '2' },
  { id: 'revocation', index: '03', label: 'Device Revocation', keyHint: '3' },
  { id: 'cap', index: '04', label: 'Cap Intelligence', keyHint: '4' },
];

interface NavRailProps {
  incidentCount?: number;
  isDisasterActive?: boolean;
}

export function NavRail({ incidentCount = 0, isDisasterActive = false }: NavRailProps) {
  const [activeSection, setActiveSection] = useState('incidents');

  // Keyboard shortcut listeners (1, 2, 3, 4) for instant operational navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't trigger when user is typing in an input/textarea/select
      if (
        ['INPUT', 'TEXTAREA', 'SELECT'].includes((e.target as HTMLElement)?.tagName)
      ) {
        return;
      }

      const match = SECTIONS.find((s) => s.keyHint === e.key);
      if (match) {
        e.preventDefault();
        const target = document.getElementById(match.id);
        if (target) {
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
          setActiveSection(match.id);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Intersection observer for section tracking
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveSection(entry.target.id);
          }
        }
      },
      { rootMargin: '-20% 0px -60% 0px' }
    );

    SECTIONS.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, []);

  const scrollTo = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      setActiveSection(id);
    }
  };

  return (
    <nav className="operations-ribbon" aria-label="Operations Navigation & Telemetry Ribbon">
      <div className="operations-ribbon__nav">
        {SECTIONS.map((s) => (
          <button
            key={s.id}
            type="button"
            className={`ribbon-tab ${activeSection === s.id ? 'is-active' : ''}`}
            onClick={() => scrollTo(s.id)}
            aria-current={activeSection === s.id ? 'page' : undefined}
          >
            <span className="ribbon-tab__key" aria-hidden="true">[{s.keyHint}]</span>
            <span>{s.label}</span>
            {s.id === 'incidents' && incidentCount > 0 && (
              <span className="tag tag--danger tag--xs">{incidentCount}</span>
            )}
            {s.id === 'disaster' && isDisasterActive && (
              <span className="tag tag--warning tag--xs">ACTIVE</span>
            )}
          </button>
        ))}
      </div>

      <div className="operations-ribbon__stats" aria-label="Live Network Metrics">
        <div className="stat-metric">
          <span className="stat-metric__label">Settlement Engine:</span>
          <span className="stat-metric__value stat-metric__value--accent">100% INTEGRITY</span>
        </div>
        <div className="stat-metric">
          <span className="stat-metric__label">Double-Spend Queue:</span>
          <span className={`stat-metric__value ${incidentCount > 0 ? 'stat-metric__value--danger' : 'stat-metric__value--accent'}`}>
            {incidentCount} DETECTED
          </span>
        </div>
        <div className="stat-metric">
          <span className="stat-metric__label">Disaster Mode:</span>
          <span className={`stat-metric__value ${isDisasterActive ? 'stat-metric__value--warning' : ''}`}>
            {isDisasterActive ? 'BROADCASTING' : 'STANDBY'}
          </span>
        </div>
        <div className="stat-metric">
          <span className="stat-metric__label">CRL Propagate:</span>
          <span className="stat-metric__value mono">v4.8-SYNC</span>
        </div>
      </div>
    </nav>
  );
}
