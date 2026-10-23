// REST Client for backend communication

const API_BASE = '/api';

export const api = {
  // Health
  async getHealth() {
    const res = await fetch(`${API_BASE}/health`);
    if (!res.ok) throw new Error('Backend indisponibil');
    return res.json();
  },

  // Sessions
  async createSession(title = 'Ședință Nouă', language = 'ro') {
    const res = await fetch(`${API_BASE}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, language })
    });
    if (!res.ok) throw new Error('Eroare la crearea sesiunii');
    return res.json();
  },

  async listSessions(limit = 50, offset = 0) {
    const res = await fetch(`${API_BASE}/sessions?limit=${limit}&offset=${offset}`);
    if (!res.ok) throw new Error('Eroare la încărcarea istoricului');
    return res.json();
  },

  async getSession(sessionId) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}`);
    if (!res.ok) throw new Error('Eroare la obținerea detaliilor sesiunii');
    return res.json();
  },

  async updateSession(sessionId, updates) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });
    if (!res.ok) throw new Error('Eroare la actualizarea sesiunii');
    return res.json();
  },

  async deleteSession(sessionId) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}`, {
      method: 'DELETE'
    });
    if (!res.ok) throw new Error('Eroare la ștergerea sesiunii');
    return true;
  },

  async summarizeSession(sessionId) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}/summarize`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error('Eroare la generarea rezumatului');
    return res.json();
  },

  async exportSessionMarkdown(sessionId) {
    const res = await fetch(`${API_BASE}/sessions/${sessionId}/export`);
    if (!res.ok) throw new Error('Eroare la exportul sesiunii');
    return res.text();
  },

  // Transcripts
  async getTranscripts(sessionId) {
    const res = await fetch(`${API_BASE}/transcripts/${sessionId}`);
    if (!res.ok) throw new Error('Eroare la încărcarea transcriptului');
    return res.json();
  },

  async addTranscript(sessionId, text, speaker = 'Participant', isFinal = true) {
    const res = await fetch(`${API_BASE}/transcripts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        session_id: sessionId,
        text,
        speaker,
        is_final: isFinal
      })
    });
    if (!res.ok) throw new Error('Eroare la salvarea replicii');
    return res.json();
  },

  // Copilot Quick Answer
  async getQuickAnswer(
    sessionId, 
    targetName = 'Olivia', 
    customInstruction = null,
    provider = null,
    apiKey = null,
    model = null
  ) {
    const res = await fetch(`${API_BASE}/copilot/quick-answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        session_id: sessionId,
        target_name: targetName,
        custom_instruction: customInstruction,
        provider,
        api_key: apiKey,
        model
      })
    });
    if (!res.ok) throw new Error('Eroare la generarea răspunsului AI');
    return res.json();
  },

  async testApiKey(provider, apiKey, model = null) {
    const res = await fetch(`${API_BASE}/copilot/test-key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider,
        api_key: apiKey,
        model
      })
    });
    if (!res.ok) throw new Error('Eroare la testarea cheii');
    return res.json();
  },

  async getSessionAnswers(sessionId) {
    const res = await fetch(`${API_BASE}/copilot/answers/${sessionId}`);
    if (!res.ok) throw new Error('Eroare la încărcarea răspunsurilor anterioare');
    return res.json();
  },

  async submitAnswerFeedback(answerId, feedback) {
    const res = await fetch(`${API_BASE}/copilot/answers/${answerId}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ feedback })
    });
    if (!res.ok) throw new Error('Eroare la înregistrarea feedback-ului');
    return res.json();
  }
};
