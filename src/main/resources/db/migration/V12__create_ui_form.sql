-- ============================================================================
-- V12: Create FORM table for Server-Driven Dynamic Form Engine & Seed Initial Forms
-- ============================================================================

CREATE TABLE FORM (
    form_code           VARCHAR2(50)        PRIMARY KEY,
    trx_code            VARCHAR2(20)        NOT NULL,
    module              VARCHAR2(20)        NOT NULL,
    form_title          VARCHAR2(150)       NOT NULL,
    version             NUMBER(5)           DEFAULT 1 NOT NULL,
    is_active           CHAR(1)             DEFAULT 'Y' NOT NULL,
    schema_payload      CLOB                NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- 1. Seed: FORM_CASH_DEP (Nộp tiền mặt vào tài khoản thanh toán CASA)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CASH_DEP',
    'CASH_DEP',
    'DPT',
    'Nộp Tiền Mặt Vào Tài Khoản (Cash Deposit)',
    1,
    'Y',
    '{
        "formCode": "FORM_CASH_DEP",
        "trxCode": "CASH_DEP",
        "title": "Nộp Tiền Mặt Vào Tài Khoản (CASH_DEP)",
        "submitEndpoint": "/api/v1/transactions/deposit",
        "fields": [
            {
                "name": "accountNumber",
                "label": "Tài Khoản Thụ Hưởng (CASA)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Nhập 16 chữ số tài khoản...",
                "steps": ["LOOKUP_ACCOUNT"]
            },
            {
                "name": "accountHolderName",
                "label": "Tên Chủ Tài Khoản",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Số Dư Khả Dụng Hiện Tại (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Số Tiền Nộp (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000"
            },
            {
                "name": "channelId",
                "label": "Kênh Giao Dịch",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Nội Dung Nộp Tiền",
                "type": "TEXTAREA",
                "placeholder": "Nộp tiền mặt tại quầy giao dịch..."
            }
        ]
    }'
);

-- 2. Seed: FORM_CASH_WDR (Rút tiền mặt từ tài khoản CASA)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_CASH_WDR',
    'CASH_WDR',
    'DPT',
    'Rút Tiền Mặt Tại Quầy (Cash Withdrawal)',
    1,
    'Y',
    '{
        "formCode": "FORM_CASH_WDR",
        "trxCode": "CASH_WDR",
        "title": "Rút Tiền Mặt Tại Quầy (CASH_WDR)",
        "submitEndpoint": "/api/v1/transactions/withdraw",
        "fields": [
            {
                "name": "accountNumber",
                "label": "Tài Khoản Trích Nợ (CASA)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Nhập 16 chữ số tài khoản...",
                "steps": ["LOOKUP_ACCOUNT"]
            },
            {
                "name": "accountHolderName",
                "label": "Tên Chủ Tài Khoản",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Số Dư Khả Dụng Hiện Có (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Số Tiền Rút (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000"
            },
            {
                "name": "channelId",
                "label": "Kênh Giao Dịch",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Nội Dung Rút Tiền",
                "type": "TEXTAREA",
                "placeholder": "Rút tiền mặt tại quầy giao dịch..."
            }
        ]
    }'
);

-- 3. Seed: FORM_TRF_INTERNAL (Chuyển khoản nội bộ giữa hai tài khoản CASA)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_TRF_INTERNAL',
    'TRF_INTERNAL',
    'DPT',
    'Chuyển Khoản Nội Bộ (Internal Transfer)',
    1,
    'Y',
    '{
        "formCode": "FORM_TRF_INTERNAL",
        "trxCode": "TRF_INTERNAL",
        "title": "Chuyển Khoản Nội Bộ (TRF_INTERNAL)",
        "submitEndpoint": "/api/v1/transactions/transfer",
        "fields": [
            {
                "name": "fromAccountNumber",
                "label": "Tài Khoản Nguồn (Trích Nợ)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Nhập 16 số tài khoản chuyển...",
                "steps": ["LOOKUP_SOURCE_ACCOUNT"]
            },
            {
                "name": "sourceAccountName",
                "label": "Tên Người Chuyển",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Số Dư Khả Dụng (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "toAccountNumber",
                "label": "Tài Khoản Thụ Hưởng (Báo Có)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Nhập 16 số tài khoản nhận...",
                "steps": ["LOOKUP_DEST_ACCOUNT"]
            },
            {
                "name": "destAccountName",
                "label": "Tên Người Nhận",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Số Tiền Chuyển (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000",
                "steps": ["CALCULATE_TRANSFER_FEE"]
            },
            {
                "name": "feeAmount",
                "label": "Phí Chuyển Khoản (VND)",
                "type": "CURRENCY",
                "defaultValue": 0
            },
            {
                "name": "channelId",
                "label": "Kênh Giao Dịch",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Nội Dung Chuyển Tiền",
                "type": "TEXTAREA",
                "placeholder": "Chuyển khoản nội bộ..."
            }
        ]
    }'
);

-- 4. Seed: FORM_LOAN_ORIGINATION (Mở hợp đồng vay tín dụng)
INSERT INTO FORM (form_code, trx_code, module, form_title, version, is_active, schema_payload)
VALUES (
    'FORM_LOAN_ORIGINATION',
    'LOAN_CREATE',
    'CRD',
    'Mở Hợp Đồng Vay Tín Dụng (Loan Origination)',
    1,
    'Y',
    '{
        "formCode": "FORM_LOAN_ORIGINATION",
        "trxCode": "LOAN_CREATE",
        "title": "Mở Hợp Đồng Vay Vốn (LOAN_CREATE)",
        "submitEndpoint": "/api/v1/lending/contracts",
        "fields": [
            {
                "name": "cifNumber",
                "label": "Mã CIF Khách Hàng",
                "type": "TEXT",
                "required": true,
                "placeholder": "Ví dụ: CIF-10000001...",
                "steps": ["LOOKUP_CUSTOMER"]
            },
            {
                "name": "customerName",
                "label": "Tên Khách Hàng",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "linkedCasaAccountNumber",
                "label": "Số Tài Khoản CASA Giải Ngân & Thu Nợ",
                "type": "TEXT",
                "required": true,
                "placeholder": "Nhập 16 số tài khoản CASA...",
                "steps": ["LOOKUP_LINKED_CASA"]
            },
            {
                "name": "productCode",
                "label": "Gói Sản Phẩm Tín Dụng",
                "type": "CODELIST_SELECT",
                "codeListType": "LOAN_PRODUCT",
                "defaultValue": "LN_CONSUMER_VND",
                "required": true
            },
            {
                "name": "principalAmount",
                "label": "Hạn Mức Vay Đề Nghị (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "50,000,000.0000",
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "termMonths",
                "label": "Kỳ Hạn Vay (Tháng)",
                "type": "NUMBER",
                "defaultValue": 12,
                "required": true,
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "interestMargin",
                "label": "Biên Độ Lãi Suất Đàm Phán (Margin, e.g. 0.0250)",
                "type": "NUMBER",
                "defaultValue": 0.0250,
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "monthlyEstimatedEmi",
                "label": "Ước Tính Gốc + Lãi Trả Hàng Tháng (EMI)",
                "type": "CURRENCY",
                "readOnly": true
            }
        ]
    }'
);
