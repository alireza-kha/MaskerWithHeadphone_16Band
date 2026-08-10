"""
AI Analyzer Service for Risk Assessment
Uses rule-based analysis and can be extended with LLM integration
"""

import json
import re
from typing import Dict, Optional


class RiskAIAnalyzer:
    """
    AI-powered risk analyzer that identifies risks from text
    """

    # Risk keywords by category
    RISK_KEYWORDS = {
        "Cyber Risk": ["hack", "cyber", "breach", "malware", "ransomware", "phishing", "data leak", "unauthorized access"],
        "Financial Risk": ["loss", "budget", "cash", "bankruptcy", "default", "liquidity", "credit", "market volatility"],
        "Operational Risk": ["failure", "delay", "outage", "process", "breakdown", "disruption", "error", "accident"],
        "Supply Chain Risk": ["supplier", "delivery", "logistics", "vendor", "procurement", "shortage", "disruption"],
        "Legal/Compliance Risk": ["law", "compliance", "regulation", "lawsuit", "violation", "penalty", "fine", "audit"],
        "IT Risk": ["server", "system", "software", "hardware", "network", "downtime", "crash", "technical"],
        "HR Risk": ["employee", "strike", "turnover", "workforce", "talent", "skill gap", "labor"],
        "Reputation Risk": ["brand", "reputation", "public", "media", "scandal", "trust", "image"],
        "Natural Disaster Risk": ["earthquake", "flood", "storm", "fire", "natural", "disaster", "weather"],
        "Strategic Risk": ["strategy", "competition", "market", "innovation", "merger", "acquisition"],
        "BCM Risk": ["continuity", "recovery", "backup", "failover", "emergency", "crisis", "response"]
    }

    # Category to manager mapping (can be customized)
    CATEGORY_MANAGER_MAP = {
        "Cyber Risk": 1,
        "Financial Risk": 2,
        "Operational Risk": 3,
        "Supply Chain Risk": 4,
        "Legal/Compliance Risk": 5,
        "IT Risk": 1,
        "HR Risk": 6,
        "Reputation Risk": 7,
        "Natural Disaster Risk": 8,
        "Strategic Risk": 9,
        "BCM Risk": 3
    }

    def analyze_text(self, text: str) -> Dict:
        """
        Analyze text to identify risk characteristics
        
        Args:
            text: Input text to analyze
            
        Returns:
            Dictionary with risk analysis results
        """
        text_lower = text.lower()
        
        # Check if this is a risk
        is_risk = self._is_risk(text_lower)
        
        if not is_risk:
            return {
                "is_risk": False,
                "confidence": 0.0,
                "message": "No significant risk indicators found"
            }
        
        # Identify category
        category = self._identify_category(text_lower)
        
        # Generate risk title
        risk_title = self._generate_title(text, category)
        
        # Estimate probability and impact
        probability = self._estimate_probability(text_lower)
        impact = self._estimate_impact(text_lower, category)
        
        # Calculate risk score
        risk_score = round(probability * impact, 2)
        
        # Determine priority
        priority = self._get_priority(risk_score)
        
        # Get recommended action
        recommended_action = self._generate_action(category, risk_title, priority)
        
        # Check if BCM plan is needed
        bcm_required = self._check_bcm_required(priority, category)
        
        # Suggested manager ID
        suggested_manager_id = self.CATEGORY_MANAGER_MAP.get(category, 1)
        
        return {
            "is_risk": True,
            "risk_title": risk_title,
            "risk_category": category,
            "probability": probability,
            "impact": impact,
            "risk_score": risk_score,
            "priority": priority,
            "recommended_action": recommended_action,
            "bcm_required": bcm_required,
            "suggested_manager_id": suggested_manager_id,
            "confidence": 0.85  # Base confidence for rule-based system
        }

    def _is_risk(self, text: str) -> bool:
        """Check if text contains risk indicators"""
        all_keywords = []
        for keywords in self.RISK_KEYWORDS.values():
            all_keywords.extend(keywords)
        
        for keyword in all_keywords:
            if keyword in text:
                return True
        return False

    def _identify_category(self, text: str) -> str:
        """Identify the risk category based on keywords"""
        category_scores = {}
        
        for category, keywords in self.RISK_KEYWORDS.items():
            score = sum(1 for keyword in keywords if keyword in text)
            if score > 0:
                category_scores[category] = score
        
        if category_scores:
            return max(category_scores, key=category_scores.get)
        
        return "Operational Risk"  # Default category

    def _generate_title(self, text: str, category: str) -> str:
        """Generate a concise risk title"""
        # Simple approach: use first sentence or truncate
        sentences = text.split('.')
        title = sentences[0].strip()[:100]
        
        if len(title) < 20:
            title = f"{category} Event Detected"
        
        return title

    def _estimate_probability(self, text: str) -> float:
        """Estimate probability based on urgency indicators"""
        high_prob_words = ["immediate", "critical", "urgent", "severe", "already", "confirmed"]
        med_prob_words = ["possible", "potential", "might", "could", "likely"]
        
        score = 0.5  # Base probability
        
        for word in high_prob_words:
            if word in text:
                score += 0.15
        
        for word in med_prob_words:
            if word in text:
                score += 0.05
        
        return min(score, 1.0)

    def _estimate_impact(self, text: str, category: str) -> float:
        """Estimate impact based on severity indicators"""
        high_impact_words = ["complete", "total", "major", "severe", "critical", "shutdown", "failure"]
        med_impact_words = ["partial", "moderate", "temporary", "limited", "minor"]
        
        score = 0.5  # Base impact
        
        for word in high_impact_words:
            if word in text:
                score += 0.2
        
        for word in med_impact_words:
            if word in text:
                score += 0.1
        
        # Adjust based on category
        critical_categories = ["Cyber Risk", "Financial Risk", "Natural Disaster Risk", "BCM Risk"]
        if category in critical_categories:
            score = min(score + 0.1, 1.0)
        
        return min(score, 1.0)

    def _get_priority(self, risk_score: float) -> str:
        """Determine priority level from risk score"""
        if risk_score >= 0.75:
            return "Critical"
        elif risk_score >= 0.50:
            return "High"
        elif risk_score >= 0.25:
            return "Medium"
        else:
            return "Low"

    def _generate_action(self, category: str, title: str, priority: str) -> str:
        """Generate recommended action based on category and priority"""
        actions = {
            "Cyber Risk": "Activate incident response team, isolate affected systems, conduct forensic analysis",
            "Financial Risk": "Assess financial exposure, notify finance team, implement contingency measures",
            "Operational Risk": "Identify root cause, activate backup processes, notify operations team",
            "Supply Chain Risk": "Contact alternative suppliers, assess inventory levels, update procurement strategy",
            "Legal/Compliance Risk": "Notify legal department, document evidence, prepare compliance report",
            "IT Risk": "Activate IT support, restore from backup if needed, investigate technical issue",
            "HR Risk": "Contact HR department, assess workforce impact, develop retention strategy",
            "Reputation Risk": "Prepare communication strategy, monitor media, engage PR team",
            "Natural Disaster Risk": "Activate emergency response, ensure employee safety, assess damage",
            "Strategic Risk": "Review strategic plan, conduct scenario analysis, inform executive team",
            "BCM Risk": "Activate business continuity plan, switch to backup systems, notify response team"
        }
        
        base_action = actions.get(category, "Assess situation and notify relevant stakeholders")
        
        if priority == "Critical":
            return f"URGENT: {base_action}"
        elif priority == "High":
            return f"PRIORITY: {base_action}"
        else:
            return base_action

    def _check_bcm_required(self, priority: str, category: str) -> bool:
        """Determine if BCM plan is required"""
        bcm_categories = ["IT Risk", "Operational Risk", "Natural Disaster Risk", "BCM Risk", "Cyber Risk"]
        
        if priority in ["Critical", "High"] and category in bcm_categories:
            return True
        
        return False


# Singleton instance
ai_analyzer = RiskAIAnalyzer()
