# AI-Powered Enterprise Risk Management System

A comprehensive risk management system with AI assistant for Enterprise Risk Management (ERM) and Business Continuity Management (BCM).

## Overview

This system implements the workflow described in the PDF:

1. **Collecting News** - Gather risk-related information from various sources
2. **AI Analysis** - Identify and categorize risks using AI
3. **Risk Manager Review** - Managers can Ignore, Modify, or Confirm risks
4. **Action Plan Generation** - Create proper action plans for confirmed risks
5. **BCM Integration** - Generate business continuity plans for critical risks
6. **Dashboard & Reporting** - Visualize risks with charts and heatmaps

## Project Structure

```
risk-management-ai/
├── app/
│   ├── main.py              # FastAPI application entry point
│   ├── database.py          # Database configuration
│   ├── models/
│   │   ├── __init__.py
│   │   └── risk.py          # SQLAlchemy models (Risk, RiskManager, BCMPlan)
│   ├── api/
│   │   ├── risks.py         # Risk endpoints
│   │   ├── managers.py      # Risk manager endpoints
│   │   ├── dashboard.py     # Dashboard and analytics endpoints
│   │   └── bcm.py           # Business continuity management endpoints
│   ├── services/
│   │   ├── __init__.py
│   │   └── ai_analyzer.py   # AI risk analysis service
│   └── utils/
├── frontend/                # Frontend components (to be implemented)
├── requirements.txt
└── README.md
```

## Features

### 1. Risk Management
- Create and manage risks manually or via AI analysis
- Categorize risks (Cyber, Financial, Operational, IT, Supply Chain, etc.)
- Track risk status: new → under_review → confirmed/ignored/modified → action_planned
- Calculate risk scores based on probability and impact

### 2. AI Assistant
- Analyze text to identify potential risks
- Automatically categorize risks
- Estimate probability and impact scores
- Suggest appropriate risk managers
- Recommend action plans

### 3. Risk Manager Workflow
- Review AI-identified risks
- **Ignore**: Dismiss false positives
- **Modify**: Correct category, title, or details
- **Confirm**: Approve with comments
- Assign risks to appropriate managers

### 4. Action Plan Generation
- Automatic generation of detailed action plans
- Categorized by immediate, corrective, and long-term actions
- Assigned responsibilities and deadlines
- Priority-based urgency levels

### 5. Business Continuity Management (BCM)
- Auto-generate BCM plans for critical risks
- Define Recovery Time Objective (RTO) and Recovery Point Objective (RPO)
- Activate plans during crises
- Test and track plan status

### 6. Dashboard & Analytics
- Summary statistics (total, new, confirmed, ignored risks)
- Risks by category (bar chart)
- Risks by status (pie chart)
- Risk heatmap (probability vs impact)
- Trend analysis over time
- Top risks by score
- Recent activity feed

## API Endpoints

### Risks
- `POST /api/risks/` - Create a new risk
- `POST /api/risks/analyze` - Analyze text with AI to identify risks
- `GET /api/risks/` - List all risks
- `GET /api/risks/{id}` - Get specific risk
- `POST /api/risks/{id}/review` - Review risk (confirm/ignore/modify)
- `POST /api/risks/{id}/action-plan` - Generate action plan
- `DELETE /api/risks/{id}` - Delete a risk

### Risk Managers
- `POST /api/managers/` - Create a risk manager
- `GET /api/managers/` - List all managers
- `GET /api/managers/{id}` - Get specific manager
- `PUT /api/managers/{id}` - Update manager
- `DELETE /api/managers/{id}` - Delete manager

### Dashboard
- `GET /api/dashboard/summary` - Get summary statistics
- `GET /api/dashboard/risks-by-category` - Risks grouped by category
- `GET /api/dashboard/risks-by-status` - Risks grouped by status
- `GET /api/dashboard/heatmap-data` - Risk heatmap data
- `GET /api/dashboard/trend-data` - Risk trends over time
- `GET /api/dashboard/top-risks` - Top risks by score
- `GET /api/dashboard/recent-activity` - Recent activity

### Business Continuity Management
- `POST /api/bcm/` - Create BCM plan
- `GET /api/bcm/` - List all BCM plans
- `GET /api/bcm/{id}` - Get specific BCM plan
- `GET /api/bcm/risk/{risk_id}` - Get BCM plans for a risk
- `PUT /api/bcm/{id}/activate` - Activate BCM plan
- `POST /api/bcm/auto-generate/{risk_id}` - Auto-generate BCM plan
- `POST /api/bcm/{id}/test` - Record BCM plan test
- `DELETE /api/bcm/{id}` - Delete BCM plan

## Installation

### Prerequisites
- Python 3.8+
- pip

### Setup

1. Navigate to the project directory:
```bash
cd risk-management-ai
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

3. Run the application:
```bash
uvicorn app.main:app --reload
```

4. Access the API documentation:
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## Usage Examples

### 1. Analyze Text for Risks

```bash
curl -X POST "http://localhost:8000/api/risks/analyze" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Server outage detected in primary data center. Critical systems are down and customers cannot access services."
  }'
```

### 2. Review a Risk

```bash
curl -X POST "http://localhost:8000/api/risks/1/review" \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "confirm",
    "comment": "Confirmed. This is a critical IT risk requiring immediate action."
  }'
```

### 3. Generate Action Plan

```bash
curl -X POST "http://localhost:8000/api/risks/1/action-plan"
```

### 4. Auto-Generate BCM Plan

```bash
curl -X POST "http://localhost:8000/api/bcm/auto-generate/1"
```

### 5. Get Dashboard Summary

```bash
curl "http://localhost:8000/api/dashboard/summary"
```

## Risk Categories

The system supports the following risk categories:
- Cyber Risk
- Financial Risk
- Operational Risk
- Supply Chain Risk
- Legal/Compliance Risk
- IT Risk
- HR Risk
- Reputation Risk
- Natural Disaster Risk
- Strategic Risk
- BCM Risk

## Risk Status Flow

```
new → under_review → confirmed → action_planned → mitigating → closed
                  ↘ ignored
                  ↘ modified → confirmed
```

## BCM Plan Status

- **draft**: Plan created but not activated
- **active**: Plan activated during crisis
- **tested**: Plan has been tested successfully
- **archived**: Plan is no longer current

## Extending the System

### Adding LLM Integration

To integrate with OpenAI or other LLM providers:

1. Install the SDK:
```bash
pip install openai
```

2. Update `app/services/ai_analyzer.py` to use LLM API instead of rule-based analysis

3. Add environment variables for API keys

### Adding News Collection

Create a news collector service in `app/services/news_collector.py`:
- RSS feed parsing
- News API integration
- Social media monitoring
- Internal report ingestion

## Technology Stack

- **Backend**: FastAPI (Python)
- **Database**: SQLite (easily switchable to PostgreSQL)
- **ORM**: SQLAlchemy
- **AI**: Rule-based analyzer (extensible to LLM APIs)
- **Documentation**: OpenAPI/Swagger

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.
