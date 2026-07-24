package com.campuscore.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardcoded list of Indian national/gazetted holidays for 2026.
 * Used to (a) prevent exams being scheduled on these dates and
 * (b) power the frontend holiday calendar feature.
 */
public final class HolidayCalendar {

    private HolidayCalendar() {}

    // date -> holiday name
    public static final Map<LocalDate, String> HOLIDAYS_2026 = new LinkedHashMap<>();

    static {
        HOLIDAYS_2026.put(LocalDate.of(2026, 1, 1),  "New Year's Day");
        HOLIDAYS_2026.put(LocalDate.of(2026, 1, 14), "Makar Sankranti / Pongal");
        HOLIDAYS_2026.put(LocalDate.of(2026, 1, 26), "Republic Day");
        HOLIDAYS_2026.put(LocalDate.of(2026, 2, 15), "Maha Shivaratri");
        HOLIDAYS_2026.put(LocalDate.of(2026, 3, 4),  "Holi");
        HOLIDAYS_2026.put(LocalDate.of(2026, 3, 21), "Eid-ul-Fitr");
        HOLIDAYS_2026.put(LocalDate.of(2026, 3, 27), "Ram Navami");
        HOLIDAYS_2026.put(LocalDate.of(2026, 4, 3),  "Good Friday");
        HOLIDAYS_2026.put(LocalDate.of(2026, 4, 14), "Ambedkar Jayanti");
        HOLIDAYS_2026.put(LocalDate.of(2026, 5, 1),  "May Day");
        HOLIDAYS_2026.put(LocalDate.of(2026, 5, 27), "Eid-ul-Adha (Bakrid)");
        HOLIDAYS_2026.put(LocalDate.of(2026, 8, 15), "Independence Day");
        HOLIDAYS_2026.put(LocalDate.of(2026, 8, 26), "Janmashtami");
        HOLIDAYS_2026.put(LocalDate.of(2026, 9, 14), "Ganesh Chaturthi");
        HOLIDAYS_2026.put(LocalDate.of(2026, 10, 2), "Gandhi Jayanti");
        HOLIDAYS_2026.put(LocalDate.of(2026, 10, 20),"Dussehra (Vijayadashami)");
        HOLIDAYS_2026.put(LocalDate.of(2026, 11, 8), "Diwali (Deepavali)");
        HOLIDAYS_2026.put(LocalDate.of(2026, 11, 24),"Guru Nanak Jayanti");
        HOLIDAYS_2026.put(LocalDate.of(2026, 12, 25),"Christmas");
    }

    public static boolean isHoliday(LocalDate date) {
        return date != null && HOLIDAYS_2026.containsKey(date);
    }

    public static String nameOf(LocalDate date) {
        return date == null ? null : HOLIDAYS_2026.get(date);
    }
}
