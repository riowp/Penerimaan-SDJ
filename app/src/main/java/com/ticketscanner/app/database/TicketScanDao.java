package com.ticketscanner.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import com.ticketscanner.app.models.TicketScan;

import java.util.List;

@Dao
public interface TicketScanDao {

    // Fix: gunakan REPLACE agar tidak crash jika ID sudah ada saat PULL
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    long insert(TicketScan scan);

    @Delete
    void delete(TicketScan scan);

    /** All records, newest first */
    @Query("SELECT * FROM ticket_scans ORDER BY scanTimestamp DESC")
    List<TicketScan> getAllScans();

    /** Records for one operational day */
    @Query("SELECT * FROM ticket_scans WHERE operationalDate = :date ORDER BY scanTimestamp ASC")
    List<TicketScan> getScansByDate(String date);

    /** Records for one operational day + shift */
    @Query("SELECT * FROM ticket_scans WHERE operationalDate = :date AND shiftNumber = :shift ORDER BY scanTimestamp ASC")
    List<TicketScan> getScansByDateAndShift(String date, int shift);

    /** Total tonnage for a day */
    @Query("SELECT COALESCE(SUM(tonnage), 0) FROM ticket_scans WHERE operationalDate = :date")
    double getTotalTonnageByDate(String date);

    /** Total tonnage per shift for a day */
    @Query("SELECT COALESCE(SUM(tonnage), 0) FROM ticket_scans WHERE operationalDate = :date AND shiftNumber = :shift")
    double getTonnageByDateAndShift(String date, int shift);

    /** Count tickets for a day */
    @Query("SELECT COUNT(*) FROM ticket_scans WHERE operationalDate = :date")
    int getTicketCountByDate(String date);

    /** Distinct operational dates, newest first */
    @Query("SELECT DISTINCT operationalDate FROM ticket_scans ORDER BY operationalDate DESC")
    List<String> getDistinctDates();

    /** Check duplicate ticket in same operational day */
    @Query("SELECT COUNT(*) FROM ticket_scans WHERE ticketNumber = :ticketNo AND operationalDate = :date")
    int countDuplicateTicket(String ticketNo, String date);

    /** Check duplicate ticket across ALL dates - no ticket number should ever repeat */
    @Query("SELECT COUNT(*) FROM ticket_scans WHERE ticketNumber = :ticketNo")
    int countTicketEver(String ticketNo);

    /** Get single record by ID */
    @Query("SELECT * FROM ticket_scans WHERE id = :id LIMIT 1")
    TicketScan getById(long id);

    /** Get single record by ticket number and date */
    @Query("SELECT * FROM ticket_scans WHERE ticketNumber = :ticketNo AND operationalDate = :date LIMIT 1")
    TicketScan getByTicketAndDate(String ticketNo, String date);

    /** Update existing record */
    @androidx.room.Update
    void update(TicketScan scan);

    /** Tonnage per stockpile untuk satu tanggal operasional */
    @Query("SELECT COALESCE(SUM(tonnage), 0) FROM ticket_scans WHERE operationalDate = :date AND stockpile = :stockpile")
    double getTonnageByDateAndStockpile(String date, String stockpile);

    /** Tonnage per stockpile per shift */
    @Query("SELECT COALESCE(SUM(tonnage), 0) FROM ticket_scans WHERE operationalDate = :date AND shiftNumber = :shift AND stockpile = :stockpile")
    double getTonnageByDateShiftAndStockpile(String date, int shift, String stockpile);

    /** Hitung record belum tersync */
    @Query("SELECT COUNT(*) FROM ticket_scans WHERE synced = 0")
    long getUnsyncedCount();

    /** Records belum tersync ke Google Sheets */
    @Query("SELECT * FROM ticket_scans WHERE synced = 0 ORDER BY scanTimestamp ASC")
    List<TicketScan> getUnsyncedScans();

    /** Records belum tersync untuk tanggal tertentu */
    @Query("SELECT * FROM ticket_scans WHERE synced = 0 AND operationalDate = :date ORDER BY scanTimestamp ASC")
    List<TicketScan> getUnsyncedScansByDate(String date);

    /** Delete all records */
    @Query("DELETE FROM ticket_scans")
    void deleteAll();
}
