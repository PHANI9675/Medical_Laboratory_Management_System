-- ─────────────────────────────────────────────────────────────────
-- MedLab Inventory & Catalog Service — Schema
-- Database : inventory_db
-- ─────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS inventory_db;
USE inventory_db;

-- ── Table 1: tests ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS tests (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code              VARCHAR(50)     NOT NULL UNIQUE,
    name              VARCHAR(255)    NOT NULL,
    price             DECIMAL(10, 2)  NOT NULL,
    turnaround_hours  INT,
    description       VARCHAR(255)
);

-- ── Table 2: inventory_items ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS inventory_items (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_name            VARCHAR(255) NOT NULL,
    quantity             INT          NOT NULL DEFAULT 0,
    unit                 VARCHAR(30)  NOT NULL,
    description          VARCHAR(255),
    low_stock_threshold  INT          NOT NULL DEFAULT 10
);

-- ─────────────────────────────────────────────────────────────────
-- Sample Data
-- ─────────────────────────────────────────────────────────────────

INSERT IGNORE INTO tests (code, name, price, turnaround_hours, description) VALUES
('CBC',   'Complete Blood Count',       12.50,  24, 'Measures RBC, WBC, platelets and hemoglobin'),
('LFT',   'Liver Function Test',        25.00,  48, 'Checks ALT, AST, bilirubin and liver enzymes'),
('RBS',   'Random Blood Sugar',          8.00,   2, 'Measures blood glucose level at any time of day'),
('URINE', 'Urine Routine Examination',   5.00,   4, 'Physical and chemical analysis of urine sample'),
('LIPID', 'Lipid Profile',              30.00,  48, 'Cholesterol, HDL, LDL and triglycerides');

INSERT IGNORE INTO inventory_items (item_name, quantity, unit, description, low_stock_threshold) VALUES
('CBC Reagent Kit',        100, 'kits',    'Reagent kit used for Complete Blood Count test', 10),
('LFT Reagent Kit',         45, 'kits',    'Reagent kit for Liver Function Test',            10),
('Blood Glucose Strips',     8, 'strips',  'Test strips for Random Blood Sugar test',        15),
('Urine Dipstick Strips',  300, 'strips',  'Dipstick strips for urine routine examination',  50),
('Lipid Profile Kit',       60, 'kits',    'Kit for cholesterol and lipid panel testing',    10),
('EDTA Blood Tubes',       500, 'tubes',   'Purple cap tubes for blood collection',          50),
('Disposable Gloves',       12, 'pairs',   'Latex-free gloves for sample handling',          20),
('Syringes 5ml',           200, 'pieces',  'Sterile syringes for blood collection',          30);
