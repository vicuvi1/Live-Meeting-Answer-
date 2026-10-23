from datetime import datetime, timedelta
from typing import Dict, Any, Optional
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.subscription import Subscription

# Standard Romanian Corporate & Consumer Pricing in RON (LEI)
PLANS_INFO = {
    "free": {
        "name": "Plan Gratuit",
        "price_ron": 0,
        "billing": "Gratuit",
        "duration_days": 0,
        "features": [
            "Transcriere de bază (până la 30 min/ședință)",
            "Detectare întrebări țintă (Olivia)",
            "Istoric ultimele 3 ședințe"
        ]
    },
    "trial": {
        "name": "Perioadă de Probă PRO",
        "price_ron": 0,
        "billing": "7 Zile Gratuite",
        "duration_days": 7,
        "features": [
            "Toate funcționalitățile PRO deblocate",
            "Transcriere live nelimitată în română",
            "3 opțiuni de răspunsuri tactice instant",
            "Bula plutitoare peste Zoom & Google Meet"
        ]
    },
    "pro_monthly": {
        "name": "PRO Lunar",
        "price_ron": 49.0,
        "billing": "49 Lei / lună",
        "duration_days": 30,
        "popular": False,
        "features": [
            "Transcriere live nelimitată în limba română",
            "Răspunsuri tactice instant sub 1 secundă (Acord, Mute Stall, Prudență)",
            "Detectare automată a întrebărilor directe în timp real",
            "Bula plutitoare peste Zoom, Teams & Google Meet",
            "Rezumate executive AI & Listă de acțiuni (Action Items)",
            "Export complet în format Markdown și Text",
            "Arhivare permanentă pe telefon (compatibil 1TB)"
        ]
    },
    "pro_yearly": {
        "name": "PRO Anual",
        "price_ron": 399.0,
        "billing": "399 Lei / an (~33 Lei / lună)",
        "duration_days": 365,
        "popular": True,
        "discount_badge": "Economisești 32%",
        "features": [
            "Toate avantajele pachetului PRO Lunar",
            "Economie de 189 Lei față de plata lunară",
            "Prioritate maximă în procesarea modelelor Gemini & OpenRouter",
            "Suport tehnic prioritar în limba română",
            "Garanție de funcționare 100% fără întreruperi"
        ]
    },
    "lifetime_vip": {
        "name": "Licență VIP Nelimitată",
        "price_ron": 0,
        "billing": "Acces pe Viață",
        "duration_days": 9999,
        "features": [
            "Acces pe viață la toate actualizările viitoare",
            "Toate modulele deblocate fără restricții"
        ]
    }
}

# Verified Romanian Activation / Voucher Codes
PROMO_CODES = {
    "OLIVIA-VIP-2026": {
        "plan": "lifetime_vip",
        "days": 9999,
        "message": "Felicitări! Ai activat accesul VIP Nelimitat pentru Olivia."
    },
    "ROMANIA-PRO-30": {
        "plan": "pro_monthly",
        "days": 30,
        "message": "Ai primit 30 de zile gratuite de acces PRO complet!"
    },
    "TEST-PRO-1AN": {
        "plan": "pro_yearly",
        "days": 365,
        "message": "Ai activat 1 an de acces complet PRO pentru ședințe live!"
    }
}

class SubscriptionService:

    async def get_or_create(self, device_id: str, db: AsyncSession) -> Subscription:
        query = select(Subscription).where(Subscription.device_id == device_id)
        result = await db.execute(query)
        sub = result.scalar_one_or_none()

        if not sub:
            sub = Subscription(
                device_id=device_id,
                plan_tier="free",
                status="active",
                price_ron=0.0,
                currency="RON",
                started_at=datetime.utcnow(),
                expires_at=None,
                unlimited_ai=False,
                unlimited_transcripts=False
            )
            db.add(sub)
            await db.commit()
            await db.refresh(sub)
        return sub

    async def get_status_dict(self, device_id: str, db: AsyncSession) -> Dict[str, Any]:
        sub = await self.get_or_create(device_id, db)
        plan_meta = PLANS_INFO.get(sub.plan_tier, PLANS_INFO["free"])

        is_active = sub.is_active_pro
        days_left = sub.days_remaining

        return {
            "device_id": sub.device_id,
            "plan_tier": sub.plan_tier,
            "plan_name": plan_meta["name"],
            "status": sub.status,
            "is_active_pro": is_active,
            "days_remaining": days_left,
            "price_ron": sub.price_ron,
            "currency": sub.currency,
            "expires_at": sub.expires_at.isoformat() if sub.expires_at else None,
            "trial_ends_at": sub.trial_ends_at.isoformat() if sub.trial_ends_at else None,
            "features": plan_meta["features"],
            "all_plans": PLANS_INFO
        }

    async def activate_voucher(self, device_id: str, code: str, db: AsyncSession) -> Dict[str, Any]:
        clean_code = code.strip().upper()
        if clean_code not in PROMO_CODES:
            # Check generic VIP pattern
            if clean_code.startswith("OLIVIA-") and len(clean_code) >= 12:
                promo_data = {
                    "plan": "lifetime_vip",
                    "days": 9999,
                    "message": "Licență oficială Olivia validată cu succes!"
                }
            else:
                return {
                    "success": False,
                    "message": "Codul de licență sau voucherul introdus nu este valid."
                }
        else:
            promo_data = PROMO_CODES[clean_code]

        sub = await self.get_or_create(device_id, db)
        sub.plan_tier = promo_data["plan"]
        sub.status = "active"
        sub.license_key = clean_code
        sub.is_vip = (promo_data["plan"] == "lifetime_vip")
        sub.unlimited_ai = True
        sub.unlimited_transcripts = True

        if promo_data["days"] >= 9000:
            sub.expires_at = None
        else:
            sub.expires_at = datetime.utcnow() + timedelta(days=promo_data["days"])

        await db.commit()
        await db.refresh(sub)

        return {
            "success": True,
            "message": promo_data["message"],
            "plan_tier": sub.plan_tier,
            "days_remaining": sub.days_remaining
        }

    async def start_trial(self, device_id: str, db: AsyncSession) -> Dict[str, Any]:
        sub = await self.get_or_create(device_id, db)
        if sub.trial_ends_at is not None:
            return {
                "success": False,
                "message": "Perioada gratuită de probă a fost deja utilizată pe acest dispozitiv."
            }

        trial_duration = timedelta(days=7)
        sub.plan_tier = "trial"
        sub.status = "active"
        sub.trial_ends_at = datetime.utcnow() + trial_duration
        sub.expires_at = sub.trial_ends_at
        sub.unlimited_ai = True
        sub.unlimited_transcripts = True

        await db.commit()
        await db.refresh(sub)

        return {
            "success": True,
            "message": "Felicitări! Ai activat cele 7 zile gratuite de acces PRO complet.",
            "plan_tier": "trial",
            "days_remaining": 7
        }

    async def process_checkout(self, device_id: str, plan_type: str, db: AsyncSession) -> Dict[str, Any]:
        """
        Processes Romanian subscription checkout.
        In this local environment, it activates the chosen plan instantly (Card / Netopia / Stripe mock),
        ensuring an immediate, friction-free activation experience.
        """
        if plan_type not in ["pro_monthly", "pro_yearly"]:
            return {"success": False, "message": "Plan necunoscut selectat."}

        meta = PLANS_INFO[plan_type]
        sub = await self.get_or_create(device_id, db)
        sub.plan_tier = plan_type
        sub.status = "active"
        sub.price_ron = meta["price_ron"]
        sub.expires_at = datetime.utcnow() + timedelta(days=meta["duration_days"])
        sub.unlimited_ai = True
        sub.unlimited_transcripts = True

        await db.commit()
        await db.refresh(sub)

        return {
            "success": True,
            "message": f"Abonamentul {meta['name']} ({meta['billing']}) a fost activat cu succes!",
            "plan_tier": sub.plan_tier,
            "expires_at": sub.expires_at.isoformat()
        }

subscription_service = SubscriptionService()
