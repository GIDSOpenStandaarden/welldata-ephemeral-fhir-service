import React from 'react';
import Header from './Header';

export const HistoryScreen = ({
  observations,
  questionnaireResponses,
  onStartQuestionnaire,
  onBack,
  loading,
  error
}) => {
  // Group observations by their linked QuestionnaireResponse
  const groupedData = groupByQuestionnaireResponse(questionnaireResponses, observations);

  return (
    <div className="app">
      <Header title="Your Health History" onBack={onBack} />
      <main className="main-content">
        {loading && <div className="loading">Loading...</div>}
        {error && <div className="error-message">{error}</div>}

        {groupedData.length === 0 ? (
          <div className="history-section">
            <p className="empty-message">No health records yet. Take a questionnaire to get started.</p>
          </div>
        ) : (
          groupedData.map((group, index) => (
            <ResponseGroup key={group.response?.id || index} group={group} />
          ))
        )}

        <div className="history-actions">
          <button className="action-button" onClick={onStartQuestionnaire}>
            Take Questionnaire Again
          </button>
        </div>
      </main>
    </div>
  );
};

// Group observations by their linked QuestionnaireResponse
const groupByQuestionnaireResponse = (responses, observations) => {
  // Create a map of response ID to response
  const responseMap = new Map();
  responses.forEach(r => responseMap.set(r.id, r));

  // Group observations by their derivedFrom reference
  const observationsByResponse = new Map();
  const unlinkedObservations = [];

  observations.forEach(obs => {
    const derivedFrom = obs.derivedFrom?.[0]?.reference;
    if (derivedFrom) {
      const responseId = derivedFrom.replace('QuestionnaireResponse/', '');
      if (!observationsByResponse.has(responseId)) {
        observationsByResponse.set(responseId, []);
      }
      observationsByResponse.get(responseId).push(obs);
    } else {
      unlinkedObservations.push(obs);
    }
  });

  // Build grouped data, sorted by date (newest first)
  const groups = [];

  responses.forEach(response => {
    groups.push({
      response,
      observations: observationsByResponse.get(response.id) || []
    });
  });

  // Sort by authored date (newest first)
  groups.sort((a, b) => {
    const dateA = a.response?.authored ? new Date(a.response.authored) : new Date(0);
    const dateB = b.response?.authored ? new Date(b.response.authored) : new Date(0);
    return dateB - dateA;
  });

  // Add unlinked observations as a separate group if any exist
  if (unlinkedObservations.length > 0) {
    groups.push({
      response: null,
      observations: unlinkedObservations
    });
  }

  return groups;
};

const ResponseGroup = ({ group }) => {
  const { response, observations } = group;
  const date = response?.authored
    ? new Date(response.authored).toLocaleString()
    : 'Unknown date';

  return (
    <section className="history-section response-group">
      <div className="response-group-header">
        <h2>Health Check - {date}</h2>
        {response && (
          <span className="response-status-badge">{response.status}</span>
        )}
      </div>

      {observations.length > 0 ? (
        <div className="observations-grid">
          {observations.map((obs, index) => (
            <ObservationCard key={obs.id || index} observation={obs} />
          ))}
        </div>
      ) : (
        <p className="empty-message">No observations recorded.</p>
      )}
    </section>
  );
};

const ObservationCard = ({ observation }) => {
  const displayName = observation.code?.coding?.[0]?.display ||
    observation.code?.coding?.[0]?.code ||
    'Unknown';

  const value = observation.valueCodeableConcept?.coding?.[0]?.display ||
    (observation.valueQuantity
      ? `${observation.valueQuantity.value} ${observation.valueQuantity.unit || ''}`
      : null) ||
    observation.valueString ||
    'N/A';

  const category = observation.category?.[0]?.coding?.[0]?.display ||
    observation.category?.[0]?.coding?.[0]?.code ||
    '';

  return (
    <div className="observation-card">
      <div className="observation-category">{category}</div>
      <div className="observation-code">{displayName}</div>
      <div className="observation-value">{value}</div>
    </div>
  );
};

export default HistoryScreen;
