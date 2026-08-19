-- =====================================================
-- QUICK START SCRIPT
-- Run this in MySQL Workbench to set up the database
-- =====================================================

-- Step 1: Drop existing database (CAUTION: This will delete all data)
DROP DATABASE IF EXISTS outpass_portal;

-- Step 2: Create database
CREATE DATABASE outpass_portal
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Step 3: Select the database
USE outpass_portal;

-- Step 4: Verify database is selected
SELECT DATABASE() AS current_database;

-- Step 5: Now run the full schema.sql file
-- In MySQL Workbench: File -> Run SQL Script -> Select schema.sql

-- OR copy the entire content of schema.sql and run it here

