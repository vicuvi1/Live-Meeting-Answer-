from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.services.subscription_service import subscription_service

router = APIRouter(prefix="/api/subscription", tags=["Subscription"])

class VoucherRequest(BaseModel):
    code: str
    device_id: Optional[str] = "default_device"

class TrialRequest(BaseModel):
    device_id: Optional[str] = "default_device"

class CheckoutRequest(BaseModel):
    plan_type: str  # 'pro_monthly' or 'pro_yearly'
    device_id: Optional[str] = "default_device"

@router.get("/status")
async def get_subscription_status(
    device_id: str = Query("default_device"),
    db: AsyncSession = Depends(get_db)
):
    """Returns current subscription plan, days remaining and features list in Romanian."""
    return await subscription_service.get_status_dict(device_id, db)

@router.post("/activate-voucher")
async def activate_voucher(
    data: VoucherRequest,
    db: AsyncSession = Depends(get_db)
):
    """Activates a Romanian license key or VIP promo code."""
    result = await subscription_service.activate_voucher(data.device_id, data.code, db)
    if not result.get("success"):
        raise HTTPException(status_code=400, detail=result.get("message"))
    return result

@router.post("/start-trial")
async def start_trial(
    data: TrialRequest,
    db: AsyncSession = Depends(get_db)
):
    """Activates 7 days free PRO trial for Romanian users."""
    result = await subscription_service.start_trial(data.device_id, db)
    if not result.get("success"):
        raise HTTPException(status_code=400, detail=result.get("message"))
    return result

@router.post("/checkout")
async def process_checkout(
    data: CheckoutRequest,
    db: AsyncSession = Depends(get_db)
):
    """Processes instant Romanian plan subscription in RON (LEI)."""
    result = await subscription_service.process_checkout(data.device_id, data.plan_type, db)
    if not result.get("success"):
        raise HTTPException(status_code=400, detail=result.get("message"))
    return result
