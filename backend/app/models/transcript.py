import uuid
from datetime import datetime
from sqlalchemy import Column, String, DateTime, Float, Boolean, Text, ForeignKey, Index
from sqlalchemy.orm import relationship
from app.core.database import Base
from app.core.datetime_utils import utc_now

class TranscriptSegment(Base):
    __tablename__ = "transcript_segments"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(String(36), ForeignKey("meeting_sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    
    speaker = Column(String(100), default="Participant", nullable=False)
    text = Column(Text, nullable=False)
    
    timestamp = Column(DateTime, default=utc_now, nullable=False, index=True)
    relative_seconds = Column(Float, default=0.0)
    
    is_final = Column(Boolean, default=True)
    mentions_target = Column(Boolean, default=False)
    is_question = Column(Boolean, default=False)
    confidence = Column(Float, default=1.0)

    # Relationships
    session = relationship("MeetingSession", back_populates="transcripts")

Index("idx_session_timestamp", TranscriptSegment.session_id, TranscriptSegment.timestamp)
