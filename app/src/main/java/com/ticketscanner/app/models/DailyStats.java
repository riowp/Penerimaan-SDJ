package com.ticketscanner.app.models;

public class DailyStats {
    private String operationalDate;
    private int totalTickets;
    private double shift1Tonnage;
    private double shift2Tonnage;
    private double totalTonnage;
    private int shift1Tickets;
    private int shift2Tickets;

    public DailyStats() {}

    public String getOperationalDate()        { return operationalDate; }
    public void setOperationalDate(String v) { this.operationalDate = v; }

    public int getTotalTickets()             { return totalTickets; }
    public void setTotalTickets(int v)       { this.totalTickets = v; }

    public double getShift1Tonnage()         { return shift1Tonnage; }
    public void setShift1Tonnage(double v)   { this.shift1Tonnage = v; }

    public double getShift2Tonnage()         { return shift2Tonnage; }
    public void setShift2Tonnage(double v)   { this.shift2Tonnage = v; }

    public double getTotalTonnage()          { return totalTonnage; }
    public void setTotalTonnage(double v)    { this.totalTonnage = v; }

    public int getShift1Tickets()            { return shift1Tickets; }
    public void setShift1Tickets(int v)      { this.shift1Tickets = v; }

    public int getShift2Tickets()            { return shift2Tickets; }
    public void setShift2Tickets(int v)      { this.shift2Tickets = v; }
}
