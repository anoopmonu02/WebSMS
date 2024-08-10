package com.smsweb.sms.models.fees;

import jakarta.persistence.*;

@Entity
public class ReceiptSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Primary key

    @Column(nullable = false)
    private String branchCode; // The branch code, e.g., "UA" or "UC"

    @Column(nullable = false)
    private Integer currentValue; // The current sequence number

    @Column(nullable = false)
    private Integer year; // The year for which the sequence is being tracked

    // Constructors
    public ReceiptSequence() {}

    public ReceiptSequence(String branchCode, Integer currentValue, Integer year) {
        this.branchCode = branchCode;
        this.currentValue = currentValue;
        this.year = year;
    }

    // Getters and setters
    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public Integer getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Integer currentValue) {
        this.currentValue = currentValue;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}

