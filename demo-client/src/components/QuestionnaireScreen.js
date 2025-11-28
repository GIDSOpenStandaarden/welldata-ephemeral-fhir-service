import React from 'react';
import Header from './Header';
import { flattenQuestionnaireItems } from '../services/fhir';

export const QuestionnaireScreen = ({
  questionnaire,
  answers,
  onAnswerChange,
  onSubmit,
  onBack,
  loading,
  error
}) => {
  const flattenedItems = questionnaire ? flattenQuestionnaireItems(questionnaire.item || []) : [];

  return (
    <div className="app">
      <Header title={questionnaire?.title || 'Questionnaire'} onBack={onBack} />
      <main className="main-content">
        {loading && <div className="loading">Loading...</div>}
        {error && <div className="error-message">{error}</div>}
        {questionnaire && (
          <div className="questionnaire-container">
            <div className="questionnaire-items">
              {flattenedItems.map((item, index) => (
                <QuestionItem
                  key={item.linkId}
                  item={item}
                  index={index}
                  answers={answers}
                  onAnswerChange={onAnswerChange}
                />
              ))}
            </div>
            <div className="questionnaire-actions">
              <button
                className="submit-button"
                onClick={onSubmit}
                disabled={loading || Object.keys(answers).length === 0}
              >
                {loading ? 'Submitting...' : 'Submit Answers'}
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

const QuestionItem = ({ item, index, answers, onAnswerChange }) => {
  // Render group headers differently
  if (item.isGroup) {
    return (
      <div className="question-group">
        <h3 className="group-title">{item.text}</h3>
      </div>
    );
  }

  const currentValue = answers[item.linkId];

  return (
    <div className="question-item">
      <div className="question-content">
        <p className="question-text">{item.text}</p>
        <QuestionInput
          item={item}
          value={currentValue}
          onChange={(value) => onAnswerChange(item.linkId, value)}
        />
      </div>
    </div>
  );
};

const QuestionInput = ({ item, value, onChange }) => {
  // Handle different question types
  switch (item.type) {
    case 'decimal':
      return (
        <input
          type="number"
          step="0.1"
          className="question-input"
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Enter a number..."
        />
      );

    case 'integer':
      return (
        <input
          type="number"
          step="1"
          className="question-input"
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Enter a whole number..."
        />
      );

    case 'choice':
      return (
        <div className="answer-options">
          {item.answerOption?.map((option) => {
            const coding = option.valueCoding;
            const isSelected = value?.code === coding?.code;
            return (
              <label
                key={coding?.code || option.valueString}
                className={`answer-option ${isSelected ? 'selected' : ''}`}
              >
                <input
                  type="radio"
                  name={item.linkId}
                  checked={isSelected}
                  onChange={() => onChange(coding)}
                />
                <span>{coding?.display || option.valueString}</span>
              </label>
            );
          })}
        </div>
      );

    case 'string':
    case 'text':
      return (
        <input
          type="text"
          className="question-input"
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Enter text..."
        />
      );

    case 'boolean':
      return (
        <div className="answer-options">
          <label className={`answer-option ${value === true ? 'selected' : ''}`}>
            <input
              type="radio"
              name={item.linkId}
              checked={value === true}
              onChange={() => onChange(true)}
            />
            <span>Ja</span>
          </label>
          <label className={`answer-option ${value === false ? 'selected' : ''}`}>
            <input
              type="radio"
              name={item.linkId}
              checked={value === false}
              onChange={() => onChange(false)}
            />
            <span>Nee</span>
          </label>
        </div>
      );

    default:
      return (
        <input
          type="text"
          className="question-input"
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Enter value..."
        />
      );
  }
};

export default QuestionnaireScreen;
