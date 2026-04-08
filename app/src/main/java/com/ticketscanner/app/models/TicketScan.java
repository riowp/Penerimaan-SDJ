package com.ticketscanner.app.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ticket_scans")
public class TicketScan {

    @PrimaryKey(autoGenerate = false)
    private long id;
    private String ticketNumber;
    private double tonnage;
    private long scanTimestamp;
    private int shiftNumber;
    private String operationalDate;
    private boolean synced = false;
    private String stockpile = "";
    private String operator = "";  // username operator yang scan
    private boolean hasRemark = false;
    private double remarkTonnage = 0;
    private String remarkNote = "";

    public TicketScan() {}

    public TicketScan(String ticketNumber, double tonnage, long scanTimestamp,
                      int shiftNumber, String operationalDate, String stockpile) {
        this.ticketNumber = ticketNumber;
        this.tonnage = tonnage;
        this.scanTimestamp = scanTimestamp;
        this.shiftNumber = shiftNumber;
        this.operationalDate = operationalDate;
        this.stockpile = stockpile;
    }

    public long getId()                         { return id; }
    public void setId(long id)                 { this.id = id; }
    public String getTicketNumber()             { return ticketNumber; }
    public void setTicketNumber(String v)      { this.ticketNumber = v; }
    public double getTonnage()                 { return tonnage; }
    public void setTonnage(double v)           { this.tonnage = v; }

    /** Tonnage efektif: pakai remarkTonnage jika ada remark, else tonnage biasa */
    public double getEffectiveTonnage() {
        return (hasRemark && remarkTonnage > 0) ? remarkTonnage : tonnage;
    }
    public long getScanTimestamp()             { return scanTimestamp; }
    public void setScanTimestamp(long v)       { this.scanTimestamp = v; }
    public int getShiftNumber()                { return shiftNumber; }
    public void setShiftNumber(int v)          { this.shiftNumber = v; }
    public String getOperationalDate()         { return operationalDate; }
    public void setOperationalDate(String v)   { this.operationalDate = v; }
    public boolean isSynced()                  { return synced; }
    public void setSynced(boolean v)           { this.synced = v; }
    public String getStockpile()               { return stockpile != null ? stockpile : ""; }
    public void setStockpile(String v)         { this.stockpile = v; }
    public boolean isHasRemark()               { return hasRemark; }
    public void setHasRemark(boolean v)        { this.hasRemark = v; }
    public double getRemarkTonnage()           { return remarkTonnage; }
    public void setRemarkTonnage(double v)     { this.remarkTonnage = v; }
    public String getOperator()         { return operator != null ? operator : ""; }
    public void setOperator(String v)      { this.operator = v; }

    public String getRemarkNote()              { return remarkNote != null ? remarkNote : ""; }
    public void setRemarkNote(String v)        { this.remarkNote = v; }
}
