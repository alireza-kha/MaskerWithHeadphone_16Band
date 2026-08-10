"""
Risk API endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime

from app.database import get_db
from app.models.risk import Risk, RiskManager
from app.services.ai_analyzer import ai_analyzer

router = APIRouter()


# Pydantic models for request/response
class RiskCreate(BaseModel):
    title: str
    description: Optional[str] = None
    category: Optional[str] = None
    source: Optional[str] = "manual"


class RiskAnalyzeInput(BaseModel):
    text: str


class RiskReview(BaseModel):
    decision: str  # confirm, ignore, modify
    comment: Optional[str] = None
    new_title: Optional[str] = None
    new_category: Optional[str] = None


class RiskResponse(BaseModel):
    id: int
    title: str
    description: Optional[str]
    category: Optional[str]
    source: str
    status: str
    probability: Optional[float]
    impact: Optional[float]
    risk_score: Optional[float]
    risk_manager_id: Optional[int]
    created_at: datetime
    updated_at: datetime
    ai_analysis: Optional[dict]
    action_plan: Optional[str]
    bcm_plan: Optional[str]
    comments: Optional[str]

    class Config:
        from_attributes = True


@router.post("/", response_model=RiskResponse, status_code=status.HTTP_201_CREATED)
def create_risk(risk: RiskCreate, db: Session = Depends(get_db)):
    """
    Create a new risk manually
    """
    db_risk = Risk(
        title=risk.title,
        description=risk.description,
        category=risk.category,
        source=risk.source,
        status="new"
    )
    db.add(db_risk)
    db.commit()
    db.refresh(db_risk)
    return db_risk


@router.post("/analyze", response_model=dict)
def analyze_risk(input_data: RiskAnalyzeInput, db: Session = Depends(get_db)):
    """
    Analyze text using AI to identify risks
    This is Step One and Two: Collecting News and Initial Analysis
    """
    analysis = ai_analyzer.analyze_text(input_data.text)
    
    if not analysis["is_risk"]:
        return {
            "success": False,
            "message": analysis.get("message", "No risk detected")
        }
    
    # Create risk from analysis
    db_risk = Risk(
        title=analysis["risk_title"],
        description=input_data.text,
        category=analysis["risk_category"],
        source="ai_analysis",
        status="under_review",
        probability=analysis["probability"],
        impact=analysis["impact"],
        risk_score=analysis["risk_score"],
        risk_manager_id=analysis.get("suggested_manager_id"),
        ai_analysis=analysis
    )
    
    db.add(db_risk)
    db.commit()
    db.refresh(db_risk)
    
    return {
        "success": True,
        "message": "Risk identified and created",
        "risk_id": db_risk.id,
        "analysis": analysis
    }


@router.get("/", response_model=List[RiskResponse])
def get_risks(
    skip: int = 0,
    limit: int = 100,
    status_filter: Optional[str] = None,
    category: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Get all risks with optional filtering
    """
    query = db.query(Risk)
    
    if status_filter:
        query = query.filter(Risk.status == status_filter)
    
    if category:
        query = query.filter(Risk.category == category)
    
    risks = query.offset(skip).limit(limit).all()
    return risks


@router.get("/{risk_id}", response_model=RiskResponse)
def get_risk(risk_id: int, db: Session = Depends(get_db)):
    """
    Get a specific risk by ID
    """
    risk = db.query(Risk).filter(Risk.id == risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    return risk


@router.post("/{risk_id}/review", response_model=RiskResponse)
def review_risk(risk_id: int, review: RiskReview, db: Session = Depends(get_db)):
    """
    Review a risk - Step Four in the process
    Manager can: Ignore, Modify, or Confirm plus comments
    """
    risk = db.query(Risk).filter(Risk.id == risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    
    if review.decision == "confirm":
        risk.status = "confirmed"
        risk.comments = review.comment
    elif review.decision == "ignore":
        risk.status = "ignored"
        risk.comments = review.comment
    elif review.decision == "modify":
        risk.status = "modified"
        if review.new_title:
            risk.title = review.new_title
        if review.new_category:
            risk.category = review.new_category
        risk.comments = review.comment
    else:
        raise HTTPException(status_code=400, detail="Invalid decision. Must be: confirm, ignore, or modify")
    
    db.commit()
    db.refresh(risk)
    
    return risk


@router.post("/{risk_id}/action-plan", response_model=dict)
def generate_action_plan(risk_id: int, db: Session = Depends(get_db)):
    """
    Generate proper action plan - Step Five in the process
    """
    risk = db.query(Risk).filter(Risk.id == risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    
    if risk.status not in ["confirmed", "modified"]:
        raise HTTPException(
            status_code=400, 
            detail="Can only generate action plan for confirmed or modified risks"
        )
    
    # Generate action plan based on risk category and priority
    priority = "Medium"
    if risk.risk_score:
        if risk.risk_score >= 0.75:
            priority = "Critical"
        elif risk.risk_score >= 0.50:
            priority = "High"
        elif risk.risk_score >= 0.25:
            priority = "Medium"
        else:
            priority = "Low"
    
    action_plan = generate_detailed_action_plan(risk.category, risk.title, priority)
    
    risk.action_plan = action_plan
    risk.status = "action_planned"
    
    db.commit()
    db.refresh(risk)
    
    return {
        "risk_id": risk_id,
        "action_plan": action_plan,
        "priority": priority,
        "status": "generated"
    }


def generate_detailed_action_plan(category: str, title: str, priority: str) -> str:
    """
    Generate detailed action plan based on risk characteristics
    """
    plans = {
        "Cyber Risk": f"""
ACTION PLAN - {priority} Priority

IMMEDIATE ACTIONS (0-24 hours):
1. Activate incident response team
2. Isolate affected systems to prevent spread
3. Conduct initial forensic analysis
4. Notify cybersecurity team and management

CORRECTIVE ACTIONS (1-7 days):
1. Identify root cause of breach/attack
2. Patch vulnerabilities
3. Restore systems from clean backups
4. Implement additional security controls

LONG-TERM ACTIONS (1-4 weeks):
1. Review and update security policies
2. Conduct employee security awareness training
3. Implement advanced threat detection
4. Perform penetration testing

RESPONSIBLE: IT Security Team
DEADLINE: Based on priority level
""",
        "IT Risk": f"""
ACTION PLAN - {priority} Priority

IMMEDIATE ACTIONS (0-24 hours):
1. Activate IT support team
2. Assess system status and impact
3. Activate backup systems if needed
4. Notify affected users

CORRECTIVE ACTIONS (1-7 days):
1. Repair or replace failed components
2. Restore data from backups
3. Test system functionality
4. Document incident

LONG-TERM ACTIONS (1-4 weeks):
1. Implement redundancy measures
2. Update maintenance schedules
3. Review disaster recovery plan
4. Upgrade infrastructure if needed

RESPONSIBLE: IT Operations Team
DEADLINE: Based on priority level
""",
        "Operational Risk": f"""
ACTION PLAN - {priority} Priority

IMMEDIATE ACTIONS (0-24 hours):
1. Identify root cause of disruption
2. Activate backup processes
3. Notify operations team
4. Assess impact on business operations

CORRECTIVE ACTIONS (1-7 days):
1. Implement temporary workaround
2. Repair or replace failed processes
3. Monitor operations closely
4. Communicate with stakeholders

LONG-TERM ACTIONS (1-4 weeks):
1. Redesign vulnerable processes
2. Implement process improvements
3. Train staff on new procedures
4. Establish monitoring mechanisms

RESPONSIBLE: Operations Team
DEADLINE: Based on priority level
""",
        "Supply Chain Risk": f"""
ACTION PLAN - {priority} Priority

IMMEDIATE ACTIONS (0-24 hours):
1. Contact alternative suppliers
2. Assess current inventory levels
3. Notify procurement team
4. Evaluate impact on production

CORRECTIVE ACTIONS (1-7 days):
1. Secure alternative supply sources
2. Adjust production schedules
3. Communicate with customers
4. Monitor supplier status

LONG-TERM ACTIONS (1-4 weeks):
1. Diversify supplier base
2. Increase safety stock levels
3. Review supplier contracts
4. Develop contingency plans

RESPONSIBLE: Procurement & Supply Chain Team
DEADLINE: Based on priority level
"""
    }
    
    default_plan = f"""
ACTION PLAN - {priority} Priority

IMMEDIATE ACTIONS (0-24 hours):
1. Assess the situation
2. Notify relevant stakeholders
3. Implement immediate containment measures
4. Document initial findings

CORRECTIVE ACTIONS (1-7 days):
1. Investigate root cause
2. Implement corrective measures
3. Monitor effectiveness
4. Update risk register

LONG-TERM ACTIONS (1-4 weeks):
1. Review and update policies
2. Implement preventive measures
3. Conduct training if needed
4. Establish ongoing monitoring

RESPONSIBLE: Relevant Department
DEADLINE: Based on priority level
"""
    
    return plans.get(category, default_plan)


@router.delete("/{risk_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_risk(risk_id: int, db: Session = Depends(get_db)):
    """
    Delete a risk
    """
    risk = db.query(Risk).filter(Risk.id == risk_id).first()
    if not risk:
        raise HTTPException(status_code=404, detail="Risk not found")
    
    db.delete(risk)
    db.commit()
    return None
