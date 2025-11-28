import React from 'react';
import Header from './Header';

export const HomeScreen = ({ webId, onLogout, onStartQuestionnaire, onViewHistory, error }) => (
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
    </main>
  </div>
);

export default HomeScreen;
