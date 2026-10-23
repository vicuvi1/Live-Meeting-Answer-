// Resilient WebSocket Client for Live Captions and Instant Copilot Answers

export class WebSocketClient {
  constructor(sessionId, onMessage, onStatusChange) {
    this.sessionId = sessionId;
    this.onMessage = onMessage;
    this.onStatusChange = onStatusChange;
    this.ws = null;
    this.reconnectTimer = null;
    this.pingInterval = null;
    this.isManualClosed = false;
    this.reconnectAttempts = 0;
  }

  connect() {
    this.isManualClosed = false;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws/${this.sessionId}`;

    try {
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        this.reconnectAttempts = 0;
        if (this.onStatusChange) this.onStatusChange('connected');
        this.startHeartbeat();
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'PONG') return;
          if (this.onMessage) this.onMessage(data);
        } catch (err) {
          console.error('Error parsing WS message:', err);
        }
      };

      this.ws.onclose = () => {
        this.stopHeartbeat();
        if (this.onStatusChange) this.onStatusChange('disconnected');
        if (!this.isManualClosed) {
          this.scheduleReconnect();
        }
      };

      this.ws.onerror = (err) => {
        console.error('WebSocket encountered error:', err);
        this.ws.close();
      };
    } catch (e) {
      console.error('Failed to initiate WebSocket:', e);
      this.scheduleReconnect();
    }
  }

  startHeartbeat() {
    this.stopHeartbeat();
    this.pingInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'PING' }));
      }
    }, 15000);
  }

  stopHeartbeat() {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return;
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), 10000);
    this.reconnectAttempts++;
    if (this.onStatusChange) this.onStatusChange('reconnecting');
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  sendTranscript(text, speaker = 'Participant', isFinal = true) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'TRANSCRIPT_CHUNK',
        text,
        speaker,
        is_final: isFinal
      }));
    }
  }

  requestQuickAnswer(targetName = 'Olivia') {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'REQUEST_QUICK_ANSWER',
        target_name: targetName
      }));
    }
  }

  disconnect() {
    this.isManualClosed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}
