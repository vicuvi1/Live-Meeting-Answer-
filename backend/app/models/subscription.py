import uuid
from datetime import datetime, timedelta
from sqlalchemy import Column, String, DateTime, Boolean, Float
from app.core.database import Base
from app.core.datetime_utils import utc_now

class Subscription(Base):
    __tablename__ = "subscriptions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = Column(String(100), default="default_device", unique=True, index=True)
    
    # Tier: 'free', 'trial', 'pro_monthly', 'pro_yearly', 'lifetime_vip'
    plan_tier = Column(String(50), default="free", nullable=False)
    # Status: 'active', 'expired', 'canceled'
    status = Column(String(20), default="active", nullable=False)
    
    price_ron = Column(Float, default=0.0)
    currency = Column(String(10), default="RON")
    
    started_at = Column(DateTime, default=utc_now, nullable=False)
    expires_at = Column(DateTime, nullable=True)
    trial_ends_at = Column(DateTime, nullable=True)
    
    license_key = Column(String(100), nullable=True)
    is_vip = Column(Boolean, default=False)
    
    # Entitlements
    unlimited_ai = Column(Boolean, default=False)
    unlimited_transcripts = Column(Boolean, default=False)
    floating_bubble_priority = Column(Boolean, default=True)

    @property
    def is_active_pro(self) -> bool:
        if self.is_vip or self.plan_tier == "lifetime_vip":
            return True
        if self.status != "active":
            return False
        if self.expires_at:
            exp = self.expires_at.replace(tzinfo=None) if self.expires_at.tzinfo else self.expires_at
            if exp < utc_now():
                return False
        if self.plan_tier in ["pro_monthly", "pro_yearly", "lifetime_vip", "trial"]:
            return True
        return False

    @property
    def days_remaining(self) -> int:
        if self.is_vip or self.plan_tier == "lifetime_vip":
            return 9999
        target = self.expires_at or self.trial_ends_at
        if not target:
            return 0
        if target.tzinfo is not None:
            target = target.replace(tzinfo=None)
        diff_seconds = (target - utc_now()).total_seconds()
        if diff_seconds <= 0:
            return 0
        import math
        return max(1, math.ceil(diff_seconds / 86400.0))
