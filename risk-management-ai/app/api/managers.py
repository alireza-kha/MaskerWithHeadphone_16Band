"""
Risk Managers API endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
from datetime import datetime

from app.database import get_db
from app.models.risk import RiskManager

router = APIRouter()


class ManagerCreate(BaseModel):
    full_name: str
    department: Optional[str] = None
    email: Optional[str] = None
    phone: Optional[str] = None
    role: Optional[str] = None
    assigned_categories: Optional[str] = None


class ManagerResponse(BaseModel):
    id: int
    full_name: str
    department: Optional[str]
    email: Optional[str]
    phone: Optional[str]
    role: Optional[str]
    assigned_categories: Optional[str]

    class Config:
        from_attributes = True


@router.post("/", response_model=ManagerResponse, status_code=status.HTTP_201_CREATED)
def create_manager(manager: ManagerCreate, db: Session = Depends(get_db)):
    """
    Create a new risk manager
    """
    db_manager = RiskManager(
        full_name=manager.full_name,
        department=manager.department,
        email=manager.email,
        phone=manager.phone,
        role=manager.role,
        assigned_categories=manager.assigned_categories
    )
    db.add(db_manager)
    db.commit()
    db.refresh(db_manager)
    return db_manager


@router.get("/", response_model=List[ManagerResponse])
def get_managers(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    """
    Get all risk managers
    """
    managers = db.query(RiskManager).offset(skip).limit(limit).all()
    return managers


@router.get("/{manager_id}", response_model=ManagerResponse)
def get_manager(manager_id: int, db: Session = Depends(get_db)):
    """
    Get a specific risk manager by ID
    """
    manager = db.query(RiskManager).filter(RiskManager.id == manager_id).first()
    if not manager:
        raise HTTPException(status_code=404, detail="Manager not found")
    return manager


@router.put("/{manager_id}", response_model=ManagerResponse)
def update_manager(manager_id: int, manager: ManagerCreate, db: Session = Depends(get_db)):
    """
    Update a risk manager
    """
    db_manager = db.query(RiskManager).filter(RiskManager.id == manager_id).first()
    if not db_manager:
        raise HTTPException(status_code=404, detail="Manager not found")
    
    db_manager.full_name = manager.full_name
    db_manager.department = manager.department
    db_manager.email = manager.email
    db_manager.phone = manager.phone
    db_manager.role = manager.role
    db_manager.assigned_categories = manager.assigned_categories
    
    db.commit()
    db.refresh(db_manager)
    return db_manager


@router.delete("/{manager_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_manager(manager_id: int, db: Session = Depends(get_db)):
    """
    Delete a risk manager
    """
    db_manager = db.query(RiskManager).filter(RiskManager.id == manager_id).first()
    if not db_manager:
        raise HTTPException(status_code=404, detail="Manager not found")
    
    db.delete(db_manager)
    db.commit()
    return None
