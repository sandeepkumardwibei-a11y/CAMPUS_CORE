package com.campuscore.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HolidayCalendarTest {

    // ─────────────────────────────────────────────────────────
    // 1. IS HOLIDAY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isHoliday should return true for valid 2026 holidays")
    void isHoliday_ReturnsTrue_ForValidHolidays() {
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2026, 1, 26))); // Republic Day
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2026, 8, 15))); // Independence Day
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2026, 12, 25))); // Christmas
    }

    @Test
    @DisplayName("isHoliday should return false for non-holiday dates")
    void isHoliday_ReturnsFalse_ForRegularDays() {
        assertFalse(HolidayCalendar.isHoliday(LocalDate.of(2026, 1, 2)));
        assertFalse(HolidayCalendar.isHoliday(LocalDate.of(2026, 7, 10)));
    }

    @Test
    @DisplayName("isHoliday should return false for same day/month in a different year")
    void isHoliday_ReturnsFalse_ForDifferentYears() {
        assertFalse(HolidayCalendar.isHoliday(LocalDate.of(2025, 1, 26)));
        assertFalse(HolidayCalendar.isHoliday(LocalDate.of(2027, 8, 15)));
    }

    @Test
    @DisplayName("isHoliday should safely return false when given null input")
    void isHoliday_ReturnsFalse_WhenDateIsNull() {
        assertFalse(HolidayCalendar.isHoliday(null));
    }

    // ─────────────────────────────────────────────────────────
    // 2. NAME OF HOLIDAY TESTS
    // ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Date {0} should map to holiday name: ''{1}''")
    @CsvSource({
            "2026-01-01, New Year's Day",
            "2026-01-14, Makar Sankranti / Pongal",
            "2026-01-26, Republic Day",
            "2026-03-04, Holi",
            "2026-04-03, Good Friday",
            "2026-05-01, May Day",
            "2026-08-15, Independence Day",
            "2026-10-02, Gandhi Jayanti",
            "2026-11-08, Diwali (Deepavali)",
            "2026-12-25, Christmas"
    })
    void nameOf_ReturnsCorrectHolidayName(String dateString, String expectedName) {
        LocalDate date = LocalDate.parse(dateString);
        assertEquals(expectedName, HolidayCalendar.nameOf(date));
    }

    @Test
    @DisplayName("nameOf should return null for non-holiday dates")
    void nameOf_ReturnsNull_ForNonHolidays() {
        assertNull(HolidayCalendar.nameOf(LocalDate.of(2026, 6, 15)));
    }

    @Test
    @DisplayName("nameOf should safely return null when given null input")
    void nameOf_ReturnsNull_WhenDateIsNull() {
        assertNull(HolidayCalendar.nameOf(null));
    }

    // ─────────────────────────────────────────────────────────
    // 3. MAP STRUCTURE INTEGRITY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HOLIDAYS_2026 map should contain expected number of gazetted holidays")
    void holidaysMap_ContainsExpectedSize() {
        Map<LocalDate, String> map = HolidayCalendar.HOLIDAYS_2026;
        assertNotNull(map);
        assertEquals(19, map.size());
    }

    @Test
    @DisplayName("HOLIDAYS_2026 map should not contain any null keys or blank values")
    void holidaysMap_HasValidKeysAndValues() {
        HolidayCalendar.HOLIDAYS_2026.forEach((date, name) -> {
            assertNotNull(date, "Holiday date key must not be null");
            assertNotNull(name, "Holiday name value must not be null");
            assertFalse(name.isBlank(), "Holiday name must not be blank");
            assertEquals(2026, date.getYear(), "All configured holidays must belong to year 2026");
        });
    }
}