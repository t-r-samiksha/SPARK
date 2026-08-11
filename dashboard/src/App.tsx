import { useState } from 'react';
import { StatusBar } from './components/StatusBar';
import { NavRail } from './components/NavRail';
import { IncidentFeed } from './components/IncidentFeed';
import { DisasterControl } from './components/DisasterControl';
import { RevokeDevice } from './components/RevokeDevice';
import { CapIntelligence } from './components/CapIntelligence';

export default function App() {
  const [incidentCount, setIncidentCount] = useState(0);
  const [isDisasterActive, setIsDisasterActive] = useState(false);
  const [selectedRevokeDeviceId, setSelectedRevokeDeviceId] = useState('');

  const handleSelectRevoke = (deviceId: string) => {
    setSelectedRevokeDeviceId(deviceId);
    const target = document.getElementById('revocation');
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  };

  return (
    <div className="app tactical-grid-bg">
      <StatusBar
        incidentCount={incidentCount}
        isDisasterActive={isDisasterActive}
      />

      <NavRail
        incidentCount={incidentCount}
        isDisasterActive={isDisasterActive}
      />

      <main className="command-canvas" id="main">
        <div className="command-grid">
          {/* Primary Forensic Stream */}
          <div className="command-main">
            <IncidentFeed
              onSelectRevokeDevice={handleSelectRevoke}
              onIncidentsLoaded={setIncidentCount}
            />
          </div>

          {/* Tactical Control Sidebar */}
          <aside className="command-controls" aria-label="Operational Controls and Intelligence">
            <DisasterControl onStateChange={setIsDisasterActive} />
            <RevokeDevice initialDeviceId={selectedRevokeDeviceId} />
            <CapIntelligence />
          </aside>
        </div>
      </main>
    </div>
  );
}
