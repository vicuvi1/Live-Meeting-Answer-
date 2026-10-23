import uuid
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, Text, ForeignKey
from sqlalchemy.orm import relationship
from app.core.database import Base

class AiAnswer(Base):
    __tablename__ = "ai_answers"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(String(36), ForeignKey("meeting_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    
    question_detected = Column(Text, nullable=False)
    spoken_response = Column(Text, nullable=False)  # Default / recommended phrase
    talking_points = Column(Text, nullable=True)     # JSON array or bullet points
    tactical_responses = Column(Text, nullable=True) # JSON with agree, stall, caution cards
    context_used = Column(Text, nullable=True)       # The transcript snapshot fed to the LLM
    
    llm_model = Column(String(50), default="gemini-2.5-flash")
    latency_ms = Column(Integer, default=0)
    generated_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    
    feedback = Column(String(20), nullable=True)     # "used", "dismissed", "helpful"

    # Relationships
    session = relationship("MeetingSession", back_populates="answers")
