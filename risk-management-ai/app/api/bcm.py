"""
Business Continuity Management (BCM) API endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime

from app.database import get_db
from app.models.risk import BCMPlan, Risk

router = APIRouter()


class BCMPlanCreate(BaseModel):
    risk_id: int
    scenario: str
    recovery_strategy: str
    responsible_team: str
    rto_minutes: int  # Recovery Time Objective
    rpo_minutes: int  # Recovery Point Objective


class BCMPlanResponse(BaseModel):
    id: int
    risk_id: int
    scenario: str
    recovery_strategy: str
    responsible_team: str
    rto_minutes: int
    rpo_minutes: int
    status: str
    created_at: datetime

    class Config:
        from_attributes = True


@router.post("/", response_model=BCMPlanResponse, status_code=status.HTTP_201_CREATED)
def create_bcm_plan(plan: BCMPlanCreate, db: Session = Depends(get_db)):
    """
    Create a new BCM plan for a risk
    """
    # Verify risk exists
    risk = db.query(Risk).filter(Risk.id == plan.risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    
    db_plan = BCMPlan(
        risk_id=plan.risk_id,
        scenario=plan.scenario,
        recovery_strategy=plan.recovery_strategy,
        responsible_team=plan.responsible_team,
        rto_minutes=plan.rto_minutes,
        rpo_minutes=plan.rpo_minutes,
        status="draft"
    )
    
    db.add(db_plan)
    db.commit()
    db.refresh(db_plan)
    
    # Update risk with BCM plan reference
    risk.bcm_plan = f"BCM Plan ID: {db_plan.id}"
    db.commit()
    
    return db_plan


@router.get("/", response_model=List[BCMPlanResponse])
def get_bcm_plans(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    """
    Get all BCM plans
    """
    plans = db.query(BCMPlan).offset(skip).limit(limit).all()
    return plans


@router.get("/{plan_id}", response_model=BCMPlanResponse)
def get_bcm_plan(plan_id: int, db: Session = Depends(get_db)):
    """
    Get a specific BCM plan by ID
    """
    plan = db.query(BCMPlan).filter(BCMPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="BCM plan not found")
    return plan


@router.get("/risk/{risk_id}", response_model=List[BCMPlanResponse])
def get_bcm_plans_by_risk(risk_id: int, db: Session = Depends(get_db)):
    """
    Get BCM plans for a specific risk
    """
    plans = db.query(BCMPlan).filter(BCMPlan.risk_id == risk_id).all()
    return plans


@router.put("/{plan_id}/activate", response_model=BCMPlanResponse)
def activate_bcm_plan(plan_id: int, db: Session = Depends(get_db)):
    """
    Activate a BCM plan (used during crisis)
    """
    plan = db.query(BCMPlan).filter(BCMPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="BCM plan not found")
    
    plan.status = "active"
    db.commit()
    db.refresh(plan)
    
    return plan


@router.put("/{plan_id}/status", response_model=BCMPlanResponse)
def update_bcm_plan_status(plan_id: int, status: str, db: Session = Depends(get_db)):
    """
    Update BCM plan status (draft, active, tested, archived)
    """
    valid_statuses = ["draft", "active", "tested", "archived"]
    if status not in valid_statuses:
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid status. Must be one of: {valid_statuses}"
        )
    
    plan = db.query(BCMPlan).filter(BCMPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="BCM plan not found")
    
    plan.status = status
    db.commit()
    db.refresh(plan)
    
    return plan


@router.post("/{plan_id}/test", response_model=dict)
def test_bcm_plan(plan_id: int, db: Session = Depends(get_db)):
    """
    Record a BCM plan test
    """
    plan = db.query(BCMPlan).filter(BCMPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="BCM plan not found")
    
    plan.status = "tested"
    db.commit()
    
    return {
        "message": "BCM plan test recorded successfully",
        "plan_id": plan_id,
        "status": "tested",
        "tested_at": datetime.utcnow().isoformat()
    }


@router.delete("/{plan_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_bcm_plan(plan_id: int, db: Session = Depends(get_db)):
    """
    Delete a BCM plan
    """
    plan = db.query(BCMPlan).filter(BCMPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="BCM plan not found")
    
    db.delete(plan)
    db.commit()
    return None


@router.post("/auto-generate/{risk_id}", response_model=BCMPlanResponse)
def auto_generate_bcm_plan(risk_id: int, db: Session = Depends(get_db)):
    """
    Auto-generate BCM plan based on risk category
    This is called after risk confirmation
    """
    risk = db.query(Risk).filter(Risk.id == risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    
    # Generate BCM plan based on category
    bcm_templates = get_bcm_templates()
    
    template = bcm_templates.get(risk.category, bcm_templates["default"])
    
    db_plan = BCMPlan(
        risk_id=risk_id,
        scenario=template["scenario"].format(risk_title=risk.title),
        recovery_strategy=template["recovery_strategy"],
        responsible_team=template["responsible_team"],
        rto_minutes=template["rto_minutes"],
        rpo_minutes=template["rpo_minutes"],
        status="draft"
    )
    
    db.add(db_plan)
    db.commit()
    db.refresh(db_plan)
    
    # Update risk
    risk.bcm_plan = f"BCM Plan ID: {db_plan.id}"
    db.commit()
    
    return db_plan


def get_bcm_templates():
    """
    Get BCM plan templates by risk category
    """
    return {
        "IT Risk": {
            "scenario": "Disruption of critical IT systems: {risk_title}",
            "recovery_strategy": """
1. Activate backup servers and systems
2. Switch to disaster recovery site if needed
3. Restore data from latest backups
4. Notify IT response team
5. Implement manual workarounds for critical processes
""",
            "responsible_team": "IT Operations & Disaster Recovery Team",
            "rto_minutes": 240,  # 4 hours
            "rpo_minutes": 60    # 1 hour
        },
        "Cyber Risk": {
            "scenario": "Cybersecurity incident: {risk_title}",
            "recovery_strategy": """
1. Isolate affected systems immediately
2. Activate incident response team
3. Conduct forensic analysis
4. Eradicate threat and patch vulnerabilities
5. Restore systems from clean backups
6. Notify stakeholders and authorities if required
""",
            "responsible_team": "IT Security & Incident Response Team",
            "rto_minutes": 120,  # 2 hours
            "rpo_minutes": 30    # 30 minutes
        },
        "Operational Risk": {
            "scenario": "Operational disruption: {risk_title}",
            "recovery_strategy": """
1. Activate backup processes
2. Reallocate resources to critical functions
3. Implement temporary workarounds
4. Notify operations team and management
5. Monitor situation closely
""",
            "responsible_team": "Operations Management Team",
            "rto_minutes": 480,  # 8 hours
            "rpo_minutes": 240   # 4 hours
        },
        "Supply Chain Risk": {
            "scenario": "Supply chain disruption: {risk_title}",
            "recovery_strategy": """
1. Activate alternative suppliers
2. Increase safety stock levels
3. Adjust production schedules
4. Communicate with customers about delays
5. Expedite critical shipments
""",
            "responsible_team": "Procurement & Supply Chain Team",
            "rto_minutes": 1440,  # 24 hours
            "rpo_minutes": 0      # No data loss acceptable
        },
        "Natural Disaster Risk": {
            "scenario": "Natural disaster impact: {risk_title}",
            "recovery_strategy": """
1. Ensure employee safety first
2. Activate emergency response team
3. Assess facility damage
4. Relocate to alternate site if needed
5. Implement remote work arrangements
6. Coordinate with emergency services
""",
            "responsible_team": "Emergency Response & HR Team",
            "rto_minutes": 2880,  # 48 hours
            "rpo_minutes": 1440   # 24 hours
        },
        "default": {
            "scenario": "Business disruption: {risk_title}",
            "recovery_strategy": """
1. Assess situation and impact
2. Activate business continuity team
3. Implement contingency plans
4. Communicate with stakeholders
5. Monitor and adjust response as needed
""",
            "responsible_team": "Business Continuity Team",
            "rto_minutes": 1440,  # 24 hours
            "rpo_minutes": 480    # 8 hours
        }
    }
