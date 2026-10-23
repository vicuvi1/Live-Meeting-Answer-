import uuid
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Integer, Text, Boolean
from sqlalchemy.orm import relationship
from app.core.database import Base
from app.core.datetime_utils import utc_now

class MeetingSession(Base):
    __tablename__ = "meeting_sessions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title = Column(String(255), nullable=False, default="Ședință Nouă")
    language = Column(String(10), default="ro")
    status = Column(String(20), default="active")  # active, completed, paused
    
    start_time = Column(DateTime, default=utc_now, nullable=False)
    end_time = Column(DateTime, nullable=True)
    duration_seconds = Column(Integer, default=0)
    
    summary = Column(Text, nullable=True)
    action_items = Column(Text, nullable=True)  # JSON or markdown formatted
    
    # Relationships
    transcripts = relationship("TranscriptSegment", back_populates="session", cascade="all, delete-orphan", order_by="TranscriptSegment.timestamp")
    answers = relationship("AiAnswer", back_populates="session", cascade="all, delete-orphan", order_by="AiAnswer.generated_at")
