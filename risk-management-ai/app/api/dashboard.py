"""
Dashboard API endpoints for Risk Management
Provides summary statistics and charts data
"""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import List, Dict
from datetime import datetime, timedelta

from app.database import get_db
from app.models.risk import Risk, BCMPlan

router = APIRouter()


@router.get("/summary")
def get_dashboard_summary(db: Session = Depends(get_db)):
    """
    Get dashboard summary statistics
    """
    total_risks = db.query(Risk).count()
    new_risks = db.query(Risk).filter(Risk.status == "new").count()
    confirmed_risks = db.query(Risk).filter(Risk.status == "confirmed").count()
    ignored_risks = db.query(Risk).filter(Risk.status == "ignored").count()
    under_review = db.query(Risk).filter(Risk.status == "under_review").count()
    action_planned = db.query(Risk).filter(Risk.status == "action_planned").count()
    
    # Count by priority levels
    critical_risks = db.query(Risk).filter(
        Risk.risk_score >= 0.75
    ).count()
    
    high_risks = db.query(Risk).filter(
        (Risk.risk_score >= 0.50) & (Risk.risk_score < 0.75)
    ).count()
    
    medium_risks = db.query(Risk).filter(
        (Risk.risk_score >= 0.25) & (Risk.risk_score < 0.50)
    ).count()
    
    low_risks = db.query(Risk).filter(
        Risk.risk_score < 0.25
    ).count()
    
    # BCM plans
    bcm_plans_active = db.query(BCMPlan).filter(BCMPlan.status == "active").count()
    bcm_plans_total = db.query(BCMPlan).count()
    
    return {
        "total_risks": total_risks,
        "new_risks": new_risks,
        "confirmed_risks": confirmed_risks,
        "ignored_risks": ignored_risks,
        "under_review": under_review,
        "action_planned": action_planned,
        "critical_risks": critical_risks,
        "high_risks": high_risks,
        "medium_risks": medium_risks,
        "low_risks": low_risks,
        "bcm_plans_active": bcm_plans_active,
        "bcm_plans_total": bcm_plans_total
    }


@router.get("/risks-by-category")
def get_risks_by_category(db: Session = Depends(get_db)):
    """
    Get risks grouped by category for chart visualization
    """
    results = db.query(
        Risk.category, 
        func.count(Risk.id).label("count")
    ).group_by(Risk.category).all()
    
    return [
        {"category": category, "count": count}
        for category, count in results
    ]


@router.get("/risks-by-status")
def get_risks_by_status(db: Session = Depends(get_db)):
    """
    Get risks grouped by status for pie chart
    """
    results = db.query(
        Risk.status, 
        func.count(Risk.id).label("count")
    ).group_by(Risk.status).all()
    
    return [
        {"status": status, "count": count}
        for status, count in results
    ]


@router.get("/heatmap-data")
def get_heatmap_data(db: Session = Depends(get_db)):
    """
    Get risk heatmap data (Probability vs Impact)
    Returns a matrix of risk counts
    """
    risks = db.query(Risk).filter(
        Risk.probability.isnot(None),
        Risk.impact.isnot(None)
    ).all()
    
    # Create heatmap matrix
    # Rows: Impact (Low, Medium, High)
    # Columns: Probability (Low, Medium, High)
    heatmap = {
        "low_impact": {"low_prob": 0, "med_prob": 0, "high_prob": 0},
        "med_impact": {"low_prob": 0, "med_prob": 0, "high_prob": 0},
        "high_impact": {"low_prob": 0, "med_prob": 0, "high_prob": 0}
    }
    
    for risk in risks:
        prob = float(risk.probability) if risk.probability else 0
        impact = float(risk.impact) if risk.impact else 0
        
        # Categorize probability
        if prob < 0.33:
            prob_cat = "low_prob"
        elif prob < 0.66:
            prob_cat = "med_prob"
        else:
            prob_cat = "high_prob"
        
        # Categorize impact
        if impact < 0.33:
            impact_cat = "low_impact"
        elif impact < 0.66:
            impact_cat = "med_impact"
        else:
            impact_cat = "high_impact"
        
        heatmap[impact_cat][prob_cat] += 1
    
    return heatmap


@router.get("/trend-data")
def get_risk_trend_data(days: int = 30, db: Session = Depends(get_db)):
    """
    Get risk trend over time
    """
    cutoff_date = datetime.utcnow() - timedelta(days=days)
    
    risks = db.query(Risk).filter(
        Risk.created_at >= cutoff_date
    ).all()
    
    # Group by date
    trend_data = {}
    for risk in risks:
        date_str = risk.created_at.strftime("%Y-%m-%d")
        if date_str not in trend_data:
            trend_data[date_str] = 0
        trend_data[date_str] += 1
    
    # Convert to list format for charts
    return [
        {"date": date, "count": count}
        for date, count in sorted(trend_data.items())
    ]


@router.get("/top-risks")
def get_top_risks(limit: int = 10, db: Session = Depends(get_db)):
    """
    Get top risks by risk score
    """
    risks = db.query(Risk).filter(
        Risk.risk_score.isnot(None),
        Risk.status.notin_(["ignored", "closed"])
    ).order_by(
        Risk.risk_score.desc()
    ).limit(limit).all()
    
    return [
        {
            "id": risk.id,
            "title": risk.title,
            "category": risk.category,
            "risk_score": float(risk.risk_score) if risk.risk_score else 0,
            "status": risk.status,
            "probability": float(risk.probability) if risk.probability else 0,
            "impact": float(risk.impact) if risk.impact else 0
        }
        for risk in risks
    ]


@router.get("/recent-activity")
def get_recent_activity(limit: int = 20, db: Session = Depends(get_db)):
    """
    Get recent risk activity
    """
    risks = db.query(Risk).order_by(
        Risk.updated_at.desc()
    ).limit(limit).all()
    
    return [
        {
            "id": risk.id,
            "title": risk.title,
            "status": risk.status,
            "updated_at": risk.updated_at.isoformat(),
            "category": risk.category
        }
        for risk in risks
    ]
