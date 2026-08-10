"""
Enterprise Risk Management System with AI Assistant
Main application entry point
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api import risks, managers, dashboard, bcm
from app.database import engine, Base

# Create database tables
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="AI-Powered Enterprise Risk Management System",
    description="A comprehensive risk management system with AI assistant for ERM and BCM",
    version="1.0.0"
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(risks.router, prefix="/api/risks", tags=["Risks"])
app.include_router(managers.router, prefix="/api/managers", tags=["Risk Managers"])
app.include_router(dashboard.router, prefix="/api/dashboard", tags=["Dashboard"])
app.include_router(bcm.router, prefix="/api/bcm", tags=["Business Continuity Management"])


@app.get("/")
def root():
    return {
        "message": "Welcome to AI-Powered Enterprise Risk Management System",
        "version": "1.0.0",
        "endpoints": {
            "risks": "/api/risks",
            "managers": "/api/managers",
            "dashboard": "/api/dashboard",
            "bcm": "/api/bcm"
        }
    }


@app.get("/health")
def health_check():
    return {"status": "healthy"}
