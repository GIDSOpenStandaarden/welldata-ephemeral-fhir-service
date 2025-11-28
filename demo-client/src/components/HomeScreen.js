import React, { useState } from 'react';
import Header from './Header';

export const HomeScreen = ({ webId, onLogout, onStartQuestionnaire, onViewHistory, error }) => {
  const [showHowItWorks, setShowHowItWorks] = useState(false);

  return (
    <div className="app">
      <Header title="WellData Wellness" webId={webId} onLogout={onLogout} />
      <main className="main-content">
        <div className="welcome-section">
          <h2>Welcome!</h2>
          <p>Track your health and wellbeing with the Health Check questionnaire.</p>
        </div>
        <div className="action-cards">
          <div className="action-card" onClick={onStartQuestionnaire}>
            <div className="card-icon">
              <img src="/icon-take-questionnaire.svg" alt="Take Questionnaire" width="64" height="64" />
            </div>
            <h3>Take Questionnaire</h3>
            <p>Answer the Health Check wellbeing assessment</p>
          </div>
          <div className="action-card" onClick={onViewHistory}>
            <div className="card-icon">
              <img src="/icon-view-history.svg" alt="View History" width="64" height="64" />
            </div>
            <h3>View History</h3>
            <p>See your past observations and responses</p>
          </div>
        </div>
        {error && <div className="error-message">{error}</div>}

        {/* How It Works Section */}
        <div className="how-it-works-section">
          <button
            className="how-it-works-toggle"
            onClick={() => setShowHowItWorks(!showHowItWorks)}
          >
            <span className="toggle-icon">{showHowItWorks ? '▼' : '▶'}</span>
            How does this work?
          </button>

          {showHowItWorks && (
            <div className="how-it-works-content">
              <div className="tech-highlight">
                <div className="tech-badge">Ephemeral FHIR Service</div>
                <h3>Your Data, Your Control</h3>
                <p>
                  This application uses an innovative <strong>Ephemeral FHIR Service</strong> that
                  bridges the gap between healthcare interoperability and personal data sovereignty.
                </p>
              </div>

              <div className="problem-solution">
                <div className="problem">
                  <h4>The Challenge</h4>
                  <p>
                    <strong>FHIR</strong> (Fast Healthcare Interoperability Resources) is the global
                    standard for health data exchange, but it's designed for server-side systems with
                    complex query capabilities.
                  </p>
                  <p>
                    <strong>Solid Pods</strong> are personal data stores that give you ownership of
                    your data, but they store data as RDF (Linked Data) with limited query support.
                  </p>
                  <p className="challenge-text">
                    How do we get the best of both worlds?
                  </p>
                </div>

                <div className="solution">
                  <h4>The Solution</h4>
                  <p>
                    The <strong>Ephemeral FHIR Service</strong> acts as a smart translation layer:
                  </p>
                  <ul>
                    <li>
                      <strong>Loads</strong> your health data from your Solid Pod when you log in
                    </li>
                    <li>
                      <strong>Provides</strong> a full FHIR API with powerful search capabilities
                    </li>
                    <li>
                      <strong>Persists</strong> changes immediately back to your Pod
                    </li>
                    <li>
                      <strong>Expires</strong> automatically when your session ends
                    </li>
                  </ul>
                </div>
              </div>

              <div className="architecture-diagram">
                <img
                  src="/architecture-diagram.svg"
                  alt="Ephemeral FHIR Service Architecture"
                  className="architecture-img"
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
              </div>

              <div className="benefits">
                <h4>Why This Matters</h4>
                <div className="benefit-grid">
                  <div className="benefit-item">
                    <span className="benefit-icon">🔐</span>
                    <div>
                      <strong>Data Sovereignty</strong>
                      <p>Your health data stays in YOUR Pod, not on corporate servers</p>
                    </div>
                  </div>
                  <div className="benefit-item">
                    <span className="benefit-icon">🔄</span>
                    <div>
                      <strong>Interoperability</strong>
                      <p>Standard FHIR format means any health app can work with your data</p>
                    </div>
                  </div>
                  <div className="benefit-item">
                    <span className="benefit-icon">📱</span>
                    <div>
                      <strong>Multi-App Access</strong>
                      <p>Different wellness apps can share the same data seamlessly</p>
                    </div>
                  </div>
                  <div className="benefit-item">
                    <span className="benefit-icon">🛡️</span>
                    <div>
                      <strong>Privacy by Design</strong>
                      <p>No persistent cache - data exists only during your session</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="tech-stack">
                <h4>Technical Stack</h4>
                <div className="stack-items">
                  <span className="stack-item">FHIR R4</span>
                  <span className="stack-item">Solid Protocol</span>
                  <span className="stack-item">RDF/Turtle</span>
                  <span className="stack-item">HAPI FHIR</span>
                  <span className="stack-item">Solid OIDC</span>
                </div>
                <p className="learn-more">
                  Learn more: <a href="https://gidsopenstandaarden.github.io/welldata-implementation-guide/technical-ephemeral-fhir-service.html" target="_blank" rel="noopener noreferrer">
                    WellData Implementation Guide
                  </a>
                </p>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default HomeScreen;
