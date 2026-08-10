"""
Risk model definition
"""

from sqlalchemy import Column, Integer, String, Text, Numeric, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship
from datetime import datetime
from app.database import Base


class Risk(Base):
    """
    Risk model representing enterprise risks
    """
    __tablename__ = "risks"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(Text, nullable=False)
    description = Column(Text)
    category = Column(String(100))
    source = Column(String(100))  # news, internal_report, email, etc.
    status = Column(String(50), default="new")  # new, under_review, ignored, modified, confirmed, action_planned, mitigating, closed
    probability = Column(Numeric)  # 0 to 1
    impact = Column(Numeric)  # 0 to 1
    risk_score = Column(Numeric)  # probability * impact
    risk_manager_id = Column(Integer, ForeignKey("risk_managers.id"))
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    ai_analysis = Column(JSON)  # Store AI analysis results
    action_plan = Column(Text)
    bcm_plan = Column(Text)
    comments = Column(Text)

    # Relationships
    manager = relationship("RiskManager", back_populates="risks")
    bcm_plans = relationship("BCMPlan", back_populates="risk", cascade="all, delete-orphan")


class RiskManager(Base):
    """
    Risk Manager model
    """
    __tablename__ = "risk_managers"

    id = Column(Integer, primary_key=True, index=True)
    full_name = Column(Text, nullable=False)
    department = Column(String(100))
    email = Column(String(255))
    phone = Column(String(50))
    role = Column(String(100))
    assigned_categories = Column(String(500))  # Comma-separated categories

    # Relationships
    risks = relationship("Risk", back_populates="manager")


class BCMPlan(Base):
    """
    Business Continuity Management Plan model
    """
    __tablename__ = "bcm_plans"

    id = Column(Integer, primary_key=True, index=True)
    risk_id = Column(Integer, ForeignKey("risks.id"), nullable=False)
    scenario = Column(Text)
    recovery_strategy = Column(Text)
    responsible_team = Column(String(100))
    rto_minutes = Column(Integer)  # Recovery Time Objective
    rpo_minutes = Column(Integer)  # Recovery Point Objective
    status = Column(String(50), default="draft")  # draft, active, tested, archived
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    risk = relationship("Risk", back_populates="bcm_plans")
