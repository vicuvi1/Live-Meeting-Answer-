import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_subscription_flow():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Get initial status (free plan)
        res = await client.get("/api/subscription/status?device_id=test_device_1")
        assert res.status_code == 200
        data = res.json()
        assert data["plan_tier"] == "free"
        assert data["currency"] == "RON"

        # 2. Activate trial
        trial_res = await client.post("/api/subscription/start-trial", json={"device_id": "test_device_1"})
        assert trial_res.status_code == 200
        trial_data = trial_res.json()
        assert trial_data["success"] is True
        assert trial_data["plan_tier"] == "trial"

        # 3. Activate VIP voucher (VIP-PRO-2026)
        voucher_res = await client.post("/api/subscription/activate-voucher", json={
            "device_id": "test_device_1",
            "code": "VIP-PRO-2026"
        })
        assert voucher_res.status_code == 200
        voucher_data = voucher_res.json()
        assert voucher_data["success"] is True
        assert voucher_data["plan_tier"] == "lifetime_vip"

        # 4. Verify updated status
        status_res = await client.get("/api/subscription/status?device_id=test_device_1")
        assert status_res.status_code == 200
        status_data = status_res.json()
        assert status_data["is_active_pro"] is True
        assert status_data["plan_tier"] == "lifetime_vip"

def test_subscription_days_remaining():
    from datetime import datetime, timezone, timedelta
    from app.models.subscription import Subscription
    
    sub = Subscription(
        device_id="test_sub_days",
        expires_at=datetime.now(timezone.utc) + timedelta(hours=5)
    )
    assert sub.days_remaining == 1

    sub_expired = Subscription(
        device_id="test_sub_expired",
        expires_at=datetime.now(timezone.utc) - timedelta(hours=1)
    )
    assert sub_expired.days_remaining == 0
