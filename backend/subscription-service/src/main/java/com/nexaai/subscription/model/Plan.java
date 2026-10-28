package com.nexaai.subscription.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Entity
public class Plan {
    @Id
    private String code; // FREE, NORMAL, PRO

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // amount in INR

    @Column(nullable = false)
    private int durationDays; // e.g., 30

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType type; // FREE or PAID

    public enum PlanType { FREE, PAID }

    // Getters and setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public PlanType getType() { return type; }
    public void setType(PlanType type) { this.type = type; }
}
