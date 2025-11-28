// FHIR API Service

const FHIR_BASE_URL = '/fhir';

// Questionnaire identifier to use
export const QUESTIONNAIRE_IDENTIFIER = 'health-check-1-0';

// Create authenticated fetch function
export const createFhirClient = (accessToken) => {
  const fetchWithAuth = async (endpoint, options = {}) => {
    if (!accessToken) {
      throw new Error('Not authenticated');
    }

    const response = await fetch(`${FHIR_BASE_URL}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/fhir+json',
        'Accept': 'application/fhir+json',
        'Authorization': `Bearer ${accessToken}`,
        ...options.headers
      }
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`HTTP ${response.status}: ${text}`);
    }

    return response.json();
  };

  return {
    // Search for questionnaires
    searchQuestionnaires: (identifier) =>
      fetchWithAuth(`/Questionnaire?identifier=${encodeURIComponent(identifier)}`),

    // Search all observations
    searchObservations: () => fetchWithAuth('/Observation'),

    // Search all questionnaire responses
    searchQuestionnaireResponses: () => fetchWithAuth('/QuestionnaireResponse'),

    // Create a new resource
    create: (resourceType, resource) =>
      fetchWithAuth(`/${resourceType}`, {
        method: 'POST',
        body: JSON.stringify(resource)
      }),

    // Generic search
    search: (resourceType, params = {}) => {
      const queryString = new URLSearchParams(params).toString();
      const endpoint = queryString ? `/${resourceType}?${queryString}` : `/${resourceType}`;
      return fetchWithAuth(endpoint);
    }
  };
};

// Mapping from questionnaire linkId to Observation code
// Based on the health-check questionnaire structure
const OBSERVATION_MAPPINGS = {
  // Vitals (decimal/integer values with units)
  'body-weight': {
    code: { system: 'http://snomed.info/sct', code: '27113001', display: 'Body weight' },
    category: 'vital-signs',
    valueType: 'quantity',
    unit: 'kg',
    unitCode: 'kg',
    unitSystem: 'http://unitsofmeasure.org'
  },
  'body-height': {
    code: { system: 'http://snomed.info/sct', code: '50373000', display: 'Body height' },
    category: 'vital-signs',
    valueType: 'quantity',
    unit: 'cm',
    unitCode: 'cm',
    unitSystem: 'http://unitsofmeasure.org'
  },
  'waist-circumference': {
    code: { system: 'http://snomed.info/sct', code: '276361009', display: 'Waist circumference' },
    category: 'vital-signs',
    valueType: 'quantity',
    unit: 'cm',
    unitCode: 'cm',
    unitSystem: 'http://unitsofmeasure.org'
  },
  'systolic-bp': {
    code: { system: 'http://snomed.info/sct', code: '271649006', display: 'Systolic blood pressure' },
    category: 'vital-signs',
    valueType: 'quantity',
    unit: 'mmHg',
    unitCode: 'mm[Hg]',
    unitSystem: 'http://unitsofmeasure.org'
  },
  // Lab values
  'cholesterol-total': {
    code: { system: 'http://snomed.info/sct', code: '77068002', display: 'Total cholesterol' },
    category: 'laboratory',
    valueType: 'quantity',
    unit: 'mg/dL',
    unitCode: 'mg/dL',
    unitSystem: 'http://unitsofmeasure.org'
  },
  'cholesterol-hdl': {
    code: { system: 'http://snomed.info/sct', code: '102737005', display: 'HDL cholesterol' },
    category: 'laboratory',
    valueType: 'quantity',
    unit: 'mg/dL',
    unitCode: 'mg/dL',
    unitSystem: 'http://unitsofmeasure.org'
  },
  // Wellbeing (coded values)
  'mood': {
    code: { system: 'http://loinc.org', code: '72166-2', display: 'Mood' },
    category: 'survey',
    valueType: 'coding'
  },
  'stress': {
    code: { system: 'http://loinc.org', code: '68011-6', display: 'Stress level' },
    category: 'survey',
    valueType: 'coding'
  },
  'daily-life': {
    code: { system: 'http://loinc.org', code: '91621-3', display: 'Daily life functioning' },
    category: 'survey',
    valueType: 'coding'
  },
  'social-contact': {
    code: { system: 'http://loinc.org', code: '61581-5', display: 'Satisfaction with social contacts' },
    category: 'social-history',
    valueType: 'coding'
  },
  'physical-limitation': {
    code: { system: 'http://snomed.info/sct', code: '32572006', display: 'Physical disability' },
    category: 'survey',
    valueType: 'coding'
  },
  // Smoking
  'smoking-status': {
    code: { system: 'http://loinc.org', code: '63638-1', display: 'Smoking status' },
    category: 'social-history',
    valueType: 'coding'
  },
  'cigarettes-per-day': {
    code: { system: 'http://loinc.org', code: '63640-7', display: 'Cigarettes smoked per day' },
    category: 'social-history',
    valueType: 'quantity',
    unit: '/d',
    unitCode: '/d',
    unitSystem: 'http://unitsofmeasure.org'
  },
  // Alcohol
  'alcohol-status': {
    code: { system: 'http://snomed.info/sct', code: '897148007', display: 'Alcohol drinking behavior' },
    category: 'social-history',
    valueType: 'coding'
  },
  'alcohol-frequency': {
    code: { system: 'http://loinc.org', code: '68518-0', display: 'How often do you have a drink containing alcohol' },
    category: 'social-history',
    valueType: 'coding'
  },
  'alcohol-normal-consumption': {
    code: { system: 'http://loinc.org', code: '68519-8', display: 'How many standard drinks containing alcohol do you have on a typical day' },
    category: 'social-history',
    valueType: 'coding'
  },
  'alcohol-excessive-consumption': {
    code: { system: 'http://loinc.org', code: '68520-6', display: 'How often do you have 6 or more drinks on 1 occasion' },
    category: 'social-history',
    valueType: 'coding'
  },
  // Activity
  'physical-exercise': {
    code: { system: 'http://snomed.info/sct', code: '228450008', display: 'Physical activity' },
    category: 'activity',
    valueType: 'quantity',
    unit: 'min/wk',
    unitCode: 'min/wk',
    unitSystem: 'http://unitsofmeasure.org'
  }
};

// Map questionnaire answers to FHIR Observations
export const createObservationsFromAnswers = (answers, questionnaireResponseId, timestamp = null) => {
  const effectiveDateTime = timestamp || new Date().toISOString();
  const observations = [];

  for (const [linkId, value] of Object.entries(answers)) {
    const mapping = OBSERVATION_MAPPINGS[linkId];
    if (!mapping || value === undefined || value === null || value === '') continue;

    const observation = {
      resourceType: 'Observation',
      status: 'final',
      category: [{
        coding: [{
          system: 'http://terminology.hl7.org/CodeSystem/observation-category',
          code: mapping.category,
          display: mapping.category.charAt(0).toUpperCase() + mapping.category.slice(1).replace('-', ' ')
        }]
      }],
      code: {
        coding: [mapping.code]
      },
      effectiveDateTime: effectiveDateTime,
      derivedFrom: [{
        reference: `QuestionnaireResponse/${questionnaireResponseId}`
      }]
    };

    // Set the value based on type
    if (mapping.valueType === 'quantity') {
      observation.valueQuantity = {
        value: parseFloat(value),
        unit: mapping.unit,
        system: mapping.unitSystem,
        code: mapping.unitCode
      };
    } else if (mapping.valueType === 'coding') {
      // Value is already a coding object from the questionnaire
      if (typeof value === 'object' && value.code) {
        observation.valueCodeableConcept = {
          coding: [value]
        };
      }
    }

    observations.push(observation);
  }

  return observations;
};

// Create a QuestionnaireResponse from answers with proper structure
export const createQuestionnaireResponse = (questionnaire, answers, timestamp = null) => {
  const authored = timestamp || new Date().toISOString();
  const buildItems = (questionnaireItems) => {
    const responseItems = [];

    for (const qItem of questionnaireItems) {
      const responseItem = { linkId: qItem.linkId };

      if (qItem.text) {
        responseItem.text = qItem.text;
      }

      // If this is a group, recursively build child items
      if (qItem.type === 'group' && qItem.item) {
        const childItems = buildItems(qItem.item);
        if (childItems.length > 0) {
          responseItem.item = childItems;
          responseItems.push(responseItem);
        }
      } else {
        // Check if we have an answer for this item
        const answer = answers[qItem.linkId];
        if (answer !== undefined && answer !== null && answer !== '') {
          // Build the answer based on the question type
          if (qItem.type === 'decimal') {
            responseItem.answer = [{ valueDecimal: parseFloat(answer) }];
          } else if (qItem.type === 'integer') {
            responseItem.answer = [{ valueInteger: parseInt(answer, 10) }];
          } else if (qItem.type === 'choice' && typeof answer === 'object') {
            responseItem.answer = [{ valueCoding: answer }];
          } else if (qItem.type === 'string' || qItem.type === 'text') {
            responseItem.answer = [{ valueString: answer }];
          } else if (qItem.type === 'boolean') {
            responseItem.answer = [{ valueBoolean: answer === true || answer === 'true' }];
          }
          responseItems.push(responseItem);
        }
      }
    }

    return responseItems;
  };

  return {
    resourceType: 'QuestionnaireResponse',
    meta: {
      profile: ['https://gidsopenstandaarden.github.io/welldata-implementation-guide/StructureDefinition/WellDataQuestionnaireResponse']
    },
    questionnaire: questionnaire.url || `Questionnaire/${questionnaire.id}`,
    status: 'completed',
    authored: authored,
    item: buildItems(questionnaire.item || [])
  };
};

// Flatten questionnaire items for rendering (handles groups)
export const flattenQuestionnaireItems = (items, parentPath = '') => {
  const flattened = [];

  for (const item of items) {
    const path = parentPath ? `${parentPath}.${item.linkId}` : item.linkId;

    if (item.type === 'group') {
      // Add group header
      flattened.push({
        ...item,
        path,
        isGroup: true
      });
      // Recursively flatten children
      if (item.item) {
        flattened.push(...flattenQuestionnaireItems(item.item, path));
      }
    } else {
      flattened.push({
        ...item,
        path,
        isGroup: false
      });
    }
  }

  return flattened;
};
