import React from 'react';
import Header from './Header';

export const HistoryScreen = ({
  observations,
  questionnaireResponses,
  onStartQuestionnaire,
  onBack,
  loading,
  error
}) => (
  <div className="app">
    <Header title="Your Health History" onBack={onBack} />
    <main className="main-content">
      {loading && <div className="loading">Loading...</div>}
      {error && <div className="error-message">{error}</div>}

      <ObservationsSection observations={observations} />
      <ResponsesSection responses={questionnaireResponses} />

      <div className="history-actions">
        <button className="action-button" onClick={onStartQuestionnaire}>
          Take Questionnaire Again
        </button>
      </div>
    </main>
  </div>
);

const ObservationsSection = ({ observations }) => (
  <section className="history-section">
    <h2>Observations ({observations.length})</h2>
    {observations.length === 0 ? (
      <p className="empty-message">No observations recorded yet.</p>
    ) : (
      <div className="observations-list">
        {observations.map((obs, index) => (
          <ObservationCard key={obs.id || index} observation={obs} />
        ))}
      </div>
    )}
  </section>
);

const ObservationCard = ({ observation }) => (
  <div className="observation-card">
    <div className="observation-header">
      <span className="observation-code">
        {observation.code?.coding?.[0]?.display || observation.code?.coding?.[0]?.code || 'Unknown'}
      </span>
      <span className="observation-date">
        {observation.effectiveDateTime
          ? new Date(observation.effectiveDateTime).toLocaleDateString()
          : 'N/A'}
      </span>
    </div>
    <div className="observation-value">
      {observation.valueCodeableConcept?.coding?.[0]?.display ||
        (observation.valueQuantity
          ? `${observation.valueQuantity.value} ${observation.valueQuantity.unit || ''}`
          : null) ||
        observation.valueString ||
        'N/A'}
    </div>
    <div className="observation-status">Status: {observation.status}</div>
  </div>
);

const ResponsesSection = ({ responses }) => (
  <section className="history-section">
    <h2>Questionnaire Responses ({responses.length})</h2>
    {responses.length === 0 ? (
      <p className="empty-message">No questionnaire responses yet.</p>
    ) : (
      <div className="responses-list">
        {responses.map((qr, index) => (
          <ResponseCard key={qr.id || index} response={qr} />
        ))}
      </div>
    )}
  </section>
);

const ResponseCard = ({ response }) => (
  <div className="response-card">
    <div className="response-header">
      <span className="response-status">{response.status}</span>
      <span className="response-date">
        {response.authored ? new Date(response.authored).toLocaleString() : 'N/A'}
      </span>
    </div>
    <div className="response-answers">{response.item?.length || 0} answers recorded</div>
  </div>
);

export default HistoryScreen;
