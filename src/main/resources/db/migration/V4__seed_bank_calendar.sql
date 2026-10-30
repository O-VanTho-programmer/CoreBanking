-- ============================================================================
-- V4: Seed Bank Calendar (2026-10-14 through 2027-12-31)
-- Populates calendar dates, setting SAT/SUN as non-working days ('N') and weekdays as working days ('Y').
-- ============================================================================

INSERT INTO BANK_CALENDAR (calendar_date, working_date, is_working_day, fiscal_year, fiscal_period, updated_at)
SELECT 
    dt AS calendar_date,
    dt AS working_date,
    CASE 
        WHEN TO_CHAR(dt, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH') IN ('SAT', 'SUN') THEN 'N' 
        ELSE 'Y' 
    END AS is_working_day,
    TO_NUMBER(TO_CHAR(dt, 'YYYY')) AS fiscal_year,
    TO_NUMBER(TO_CHAR(dt, 'MM')) AS fiscal_period,
    SYSTIMESTAMP AS updated_at
FROM (
    SELECT TO_DATE('2026-10-14', 'YYYY-MM-DD') + LEVEL - 1 AS dt
    FROM DUAL
    CONNECT BY LEVEL <= 450
);
