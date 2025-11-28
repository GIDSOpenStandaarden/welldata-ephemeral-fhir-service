import React, { useState, useEffect, useCallback, useMemo } from 'react';
import './App.css';

// Services
import { login, logout, handleCallback, getStoredSession, clearAuthState } from './services/auth';
import { createFhirClient, createObservationsFromAnswers, createQuestionnaireResponse, QUESTIONNAIRE_IDENTIFIER } from './services/fhir';

// Components
import { LoginScreen, HomeScreen, QuestionnaireScreen, HistoryScreen } from './components';

function App() {
  // Auth state
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [accessToken, setAccessToken] = useState(null);
  const [webId, setWebId] = useState(null);

  // App state
  const [questionnaire, setQuestionnaire] = useState(null);
  const [answers, setAnswers] = useState({});
  const [observations, setObservations] = useState([]);
  const [questionnaireResponses, setQuestionnaireResponses] = useState([]);
  const [view, setView] = useState('home');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Create FHIR client when access token changes
  const fhirClient = useMemo(
    () => (accessToken ? createFhirClient(accessToken) : null),
    [accessToken]
  );

  // Handle OAuth callback and check existing session
  useEffect(() => {
    let isProcessing = false;

    const initAuth = async () => {
      if (isProcessing) return;

      try {
        // Try to handle OAuth callback first
        const callbackSession = await handleCallback();
        if (callbackSession) {
          isProcessing = true;
          setAccessToken(callbackSession.accessToken);
          setWebId(callbackSession.webId);
          setIsLoggedIn(true);
          setLoading(false);
          return;
        }

        // Check for existing session
        const storedSession = getStoredSession();
        if (storedSession) {
          setAccessToken(storedSession.accessToken);
          setWebId(storedSession.webId);
          setIsLoggedIn(true);
        }
      } catch (err) {
        console.error('Auth error:', err);
        setError('Authentication error: ' + err.message);
        clearAuthState();
      } finally {
        setLoading(false);
      }
    };

    initAuth();

    return () => {
      isProcessing = true;
    };
  }, []);

  // Login handler
  const handleLogin = async () => {
    try {
      setError(null);
      setLoading(true);
      await login();
    } catch (err) {
      console.error('Login error:', err);
      setError('Login failed: ' + err.message);
      setLoading(false);
    }
  };

  // Logout handler
  const handleLogout = () => {
    logout();
    setIsLoggedIn(false);
    setAccessToken(null);
    setWebId(null);
    setView('home');
    setObservations([]);
    setQuestionnaireResponses([]);
  };

  // Load questionnaire
  const loadQuestionnaire = useCallback(async () => {
    if (!fhirClient) return;

    try {
      setLoading(true);
      const bundle = await fhirClient.searchQuestionnaires(QUESTIONNAIRE_IDENTIFIER);
      if (bundle.entry?.length > 0) {
        setQuestionnaire(bundle.entry[0].resource);
      } else {
        setError('Questionnaire not found');
      }
    } catch (err) {
      setError('Failed to load questionnaire: ' + err.message);
    } finally {
      setLoading(false);
    }
  }, [fhirClient]);

  // Load observations
  const loadObservations = useCallback(async () => {
    if (!fhirClient) return;

    try {
      setLoading(true);
      const bundle = await fhirClient.searchObservations();
      setObservations(bundle.entry?.map((e) => e.resource) || []);
    } catch (err) {
      setError('Failed to load observations: ' + err.message);
    } finally {
      setLoading(false);
    }
  }, [fhirClient]);

  // Load questionnaire responses
  const loadQuestionnaireResponses = useCallback(async () => {
    if (!fhirClient) return;

    try {
      const bundle = await fhirClient.searchQuestionnaireResponses();
      setQuestionnaireResponses(bundle.entry?.map((e) => e.resource) || []);
    } catch (err) {
      console.error('Failed to load questionnaire responses:', err);
    }
  }, [fhirClient]);

  // Handle answer change
  const handleAnswerChange = (linkId, value) => {
    setAnswers((prev) => ({ ...prev, [linkId]: value }));
  };

  // Submit questionnaire response
  const submitQuestionnaireResponse = async () => {
    if (!questionnaire || !fhirClient) return;

    try {
      setLoading(true);
      setError(null);

      // Use a shared timestamp for QuestionnaireResponse and all related Observations
      const timestamp = new Date().toISOString();

      // Create and save QuestionnaireResponse (pass full questionnaire for structure)
      const qrResource = createQuestionnaireResponse(questionnaire, answers, timestamp);
      const savedResponse = await fhirClient.create('QuestionnaireResponse', qrResource);

      // Create and save Observations with derivedFrom reference and same timestamp
      const observationsToCreate = createObservationsFromAnswers(answers, savedResponse.id, timestamp);
      for (const obs of observationsToCreate) {
        await fhirClient.create('Observation', obs);
      }

      // Reload data and navigate to history
      await loadObservations();
      await loadQuestionnaireResponses();
      setAnswers({});
      setView('history');
    } catch (err) {
      setError('Failed to submit: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  // Navigation handlers
  const startQuestionnaire = async () => {
    await loadQuestionnaire();
    setAnswers({});
    setView('questionnaire');
  };

  const viewHistory = async () => {
    await loadObservations();
    await loadQuestionnaireResponses();
    setView('history');
  };

  const goHome = () => setView('home');

  // Loading screen during initial auth
  if (loading && !isLoggedIn) {
    return (
      <div className="app">
        <div className="login-container">
          <div className="login-card">
            <h1>WellData Wellness</h1>
            <p>Loading...</p>
          </div>
        </div>
      </div>
    );
  }

  // Login screen
  if (!isLoggedIn) {
    return <LoginScreen onLogin={handleLogin} loading={loading} error={error} />;
  }

  // Main app screens
  switch (view) {
    case 'questionnaire':
      return (
        <QuestionnaireScreen
          questionnaire={questionnaire}
          answers={answers}
          onAnswerChange={handleAnswerChange}
          onSubmit={submitQuestionnaireResponse}
          onBack={goHome}
          loading={loading}
          error={error}
        />
      );

    case 'history':
      return (
        <HistoryScreen
          observations={observations}
          questionnaireResponses={questionnaireResponses}
          onStartQuestionnaire={startQuestionnaire}
          onBack={goHome}
          loading={loading}
          error={error}
        />
      );

    default:
      return (
        <HomeScreen
          webId={webId}
          onLogout={handleLogout}
          onStartQuestionnaire={startQuestionnaire}
          onViewHistory={viewHistory}
          error={error}
        />
      );
  }
}

export default App;
