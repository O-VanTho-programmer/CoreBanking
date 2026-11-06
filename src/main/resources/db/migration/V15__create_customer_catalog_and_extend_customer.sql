-- ============================================================================
-- V15: Create CUSTOMER_CATALOG and Extend CUSTOMER with Gender, Contact & Address
-- ============================================================================

-- 1. Create CUSTOMER_CATALOG Table for Customer Types & Segments
CREATE TABLE CUSTOMER_CATALOG (
    type_code           VARCHAR2(30)    PRIMARY KEY,
    type_name           VARCHAR2(100)   NOT NULL,
    segment             VARCHAR2(50)    NOT NULL, -- 'RETAIL', 'CORPORATE', 'SME', 'WEALTH'
    requires_tax_id     CHAR(1)         DEFAULT 'N' NOT NULL,
    min_age             NUMBER(3)       DEFAULT 18 NOT NULL,
    default_kyc_level   VARCHAR2(30)    DEFAULT 'STANDARD' NOT NULL,
    display_order       NUMBER(4)       DEFAULT 1 NOT NULL,
    is_active           CHAR(1)         DEFAULT 'Y' NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- Seed Customer Types
INSERT INTO CUSTOMER_CATALOG (type_code, type_name, segment, requires_tax_id, min_age, default_kyc_level, display_order)
VALUES ('INDIVIDUAL', 'Retail Individual Customer', 'RETAIL', 'N', 15, 'STANDARD', 1);

INSERT INTO CUSTOMER_CATALOG (type_code, type_name, segment, requires_tax_id, min_age, default_kyc_level, display_order)
VALUES ('CORPORATE', 'Corporate Enterprise', 'CORPORATE', 'Y', 18, 'ENHANCED', 2);

INSERT INTO CUSTOMER_CATALOG (type_code, type_name, segment, requires_tax_id, min_age, default_kyc_level, display_order)
VALUES ('SME', 'Small & Medium Enterprise', 'SME', 'Y', 18, 'STANDARD', 3);

INSERT INTO CUSTOMER_CATALOG (type_code, type_name, segment, requires_tax_id, min_age, default_kyc_level, display_order)
VALUES ('PRIORITY_INDIVIDUAL', 'Affluent / Priority Banking', 'WEALTH', 'N', 18, 'ENHANCED', 4);

-- 2. Extend Table CUSTOMER with Contact, Gender, and Administrative Address Columns
ALTER TABLE CUSTOMER ADD (
    gender          VARCHAR2(10)    DEFAULT 'OTHER' NOT NULL,
    phone_number    VARCHAR2(20),
    email_address   VARCHAR2(100),
    street_address  VARCHAR2(255),
    commune         VARCHAR2(100),
    district        VARCHAR2(100),
    province        VARCHAR2(100),
    country         VARCHAR2(50)    DEFAULT 'VN' NOT NULL
);

-- Create Performance Indexes for Search Queries
CREATE INDEX idx_cust_phone ON CUSTOMER(phone_number);
CREATE INDEX idx_cust_email ON CUSTOMER(email_address);
CREATE INDEX idx_cust_type ON CUSTOMER(customer_type);
CREATE INDEX idx_cust_province ON CUSTOMER(province);

-- 3. Update FORM catalog for FORM_CUST_PROFILE and FORM_CUST_CREATE
UPDATE FORM
SET schema_payload = '{
    "formCode": "FORM_CUST_PROFILE",
    "trxCode": "CUST_PROFILE",
    "formType": "SEARCH_GRID",
    "title": "Customer Profile & CIF 360",
    "actions": [
        {
            "code": "BTN_ADD_CUSTOMER",
            "label": "+ Add New Customer",
            "style": "primary",
            "actionType": "NAVIGATE_FORM",
            "targetFormCode": "FORM_CUST_CREATE"
        }
    ],
    "fields": [
        { "name": "keyword", "label": "Search by CIF, Name, National ID, Phone...", "type": "TEXT" },
        { "name": "customerType", "label": "Customer Type", "type": "SELECT", "optionsEndpoint": "/api/v1/customers/catalogue" },
        { "name": "fullName", "label": "Full Name", "type": "TEXT" },
        { "name": "phoneNumber", "label": "Phone Number", "type": "TEXT" },
        { "name": "emailAddress", "label": "Email Address", "type": "TEXT" },
        { "name": "gender", "label": "Gender", "type": "SELECT", "options": ["MALE", "FEMALE", "OTHER"] },
        { "name": "status", "label": "Status", "type": "SELECT", "options": ["ACTIVE", "SUSPENDED", "DECEASED"] },
        { "name": "kycStatus", "label": "KYC Status", "type": "SELECT", "options": ["PENDING", "VERIFIED", "REJECTED"] }
    ]
}'
WHERE form_code = 'FORM_CUST_PROFILE';

UPDATE FORM
SET schema_payload = '{
    "formCode": "FORM_CUST_CREATE",
    "trxCode": "CUST_CREATE",
    "title": "Register Customer Profile (CUST_CREATE)",
    "submitEndpoint": "/api/v1/customers",
    "fields": [
        {
            "name": "customerType",
            "label": "Customer Segment / Type",
            "type": "SELECT",
            "required": true,
            "defaultValue": "INDIVIDUAL",
            "optionsEndpoint": "/api/v1/customers/catalogue"
        },
        {
            "name": "fullName",
            "label": "Full Legal Name",
            "type": "TEXT",
            "required": true,
            "placeholder": "e.g. NGUYEN VAN A..."
        },
        {
            "name": "gender",
            "label": "Gender",
            "type": "SELECT",
            "required": true,
            "defaultValue": "MALE",
            "options": ["MALE", "FEMALE", "OTHER"]
        },
        {
            "name": "nationalId",
            "label": "National ID / Passport / Citizen ID",
            "type": "TEXT",
            "required": true,
            "placeholder": "Enter national ID or passport..."
        },
        {
            "name": "phoneNumber",
            "label": "Primary Phone Number",
            "type": "TEXT",
            "required": true,
            "placeholder": "e.g. 0901234567..."
        },
        {
            "name": "emailAddress",
            "label": "Email Address",
            "type": "TEXT",
            "placeholder": "e.g. customer@example.com..."
        },
        {
            "name": "taxNumber",
            "label": "Tax Identification Number",
            "type": "TEXT",
            "placeholder": "Optional for retail, required for corporate..."
        },
        {
            "name": "streetAddress",
            "label": "Street Address / House Number",
            "type": "TEXT",
            "placeholder": "e.g. 123 Nguyen Hue Street..."
        },
        {
            "name": "commune",
            "label": "Ward / Commune",
            "type": "TEXT",
            "placeholder": "e.g. Ben Nghe Ward..."
        },
        {
            "name": "district",
            "label": "District",
            "type": "TEXT",
            "placeholder": "e.g. District 1..."
        },
        {
            "name": "province",
            "label": "City / Province",
            "type": "TEXT",
            "placeholder": "e.g. Ho Chi Minh City..."
        },
        {
            "name": "kycStatus",
            "label": "Initial KYC Status",
            "type": "SELECT",
            "defaultValue": "VERIFIED",
            "required": true,
            "options": ["PENDING", "VERIFIED", "REJECTED"]
        }
    ],
    "actions": [
        {
            "code": "BTN_SUBMIT_CUST",
            "label": "Register Customer Profile",
            "style": "primary",
            "actionType": "SUBMIT_TRX",
            "endpoint": "/api/v1/customers"
        }
    ]
}'
WHERE form_code = 'FORM_CUST_CREATE';
