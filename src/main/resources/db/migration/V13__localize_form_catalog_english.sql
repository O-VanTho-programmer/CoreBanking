-- ============================================================================
-- V13: Localize Form Catalog schemas to English (Convention Standard)
-- ============================================================================

-- 1. Update: FORM_CASH_DEP
UPDATE FORM
SET form_title = 'Cash Deposit (CASH_DEP)',
    schema_payload = '{
        "formCode": "FORM_CASH_DEP",
        "trxCode": "CASH_DEP",
        "title": "Cash Deposit (CASH_DEP)",
        "submitEndpoint": "/api/v1/transactions/deposit",
        "fields": [
            {
                "name": "accountNumber",
                "label": "Beneficiary Account (CASA)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter 16-digit account number...",
                "steps": ["LOOKUP_ACCOUNT"]
            },
            {
                "name": "accountHolderName",
                "label": "Account Holder Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Current Available Balance (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Deposit Amount (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000"
            },
            {
                "name": "channelId",
                "label": "Channel",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Description",
                "type": "TEXTAREA",
                "placeholder": "Counter cash deposit..."
            }
        ]
    }'
WHERE form_code = 'FORM_CASH_DEP';

-- 2. Update: FORM_CASH_WDR
UPDATE FORM
SET form_title = 'Cash Withdrawal (CASH_WDR)',
    schema_payload = '{
        "formCode": "FORM_CASH_WDR",
        "trxCode": "CASH_WDR",
        "title": "Cash Withdrawal (CASH_WDR)",
        "submitEndpoint": "/api/v1/transactions/withdraw",
        "fields": [
            {
                "name": "accountNumber",
                "label": "Debit Account (CASA)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter 16-digit account number...",
                "steps": ["LOOKUP_ACCOUNT"]
            },
            {
                "name": "accountHolderName",
                "label": "Account Holder Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Current Available Balance (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Withdrawal Amount (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000"
            },
            {
                "name": "channelId",
                "label": "Channel",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Description",
                "type": "TEXTAREA",
                "placeholder": "Counter cash withdrawal..."
            }
        ]
    }'
WHERE form_code = 'FORM_CASH_WDR';

-- 3. Update: FORM_TRF_INTERNAL
UPDATE FORM
SET form_title = 'Internal Fund Transfer (TRF_INTERNAL)',
    schema_payload = '{
        "formCode": "FORM_TRF_INTERNAL",
        "trxCode": "TRF_INTERNAL",
        "title": "Internal Fund Transfer (TRF_INTERNAL)",
        "submitEndpoint": "/api/v1/transactions/transfer",
        "fields": [
            {
                "name": "fromAccountNumber",
                "label": "Source Account (Debit)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter 16-digit source account...",
                "steps": ["LOOKUP_SOURCE_ACCOUNT"]
            },
            {
                "name": "sourceAccountName",
                "label": "Sender Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "availableBalance",
                "label": "Available Balance (VND)",
                "type": "CURRENCY",
                "readOnly": true
            },
            {
                "name": "toAccountNumber",
                "label": "Beneficiary Account (Credit)",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter 16-digit beneficiary account...",
                "steps": ["LOOKUP_DEST_ACCOUNT"]
            },
            {
                "name": "destAccountName",
                "label": "Beneficiary Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "amount",
                "label": "Transfer Amount (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "0.0000",
                "steps": ["CALCULATE_TRANSFER_FEE"]
            },
            {
                "name": "feeAmount",
                "label": "Transfer Fee (VND)",
                "type": "CURRENCY",
                "defaultValue": 0
            },
            {
                "name": "channelId",
                "label": "Channel",
                "type": "CODELIST_SELECT",
                "codeListType": "CHANNEL_ID",
                "defaultValue": "BRANCH",
                "required": true
            },
            {
                "name": "narration",
                "label": "Description",
                "type": "TEXTAREA",
                "placeholder": "Internal account transfer..."
            }
        ]
    }'
WHERE form_code = 'FORM_TRF_INTERNAL';

-- 4. Update: FORM_LOAN_ORIGINATION
UPDATE FORM
SET form_title = 'Loan Contract Origination (LOAN_CREATE)',
    schema_payload = '{
        "formCode": "FORM_LOAN_ORIGINATION",
        "trxCode": "LOAN_CREATE",
        "title": "Loan Contract Origination (LOAN_CREATE)",
        "submitEndpoint": "/api/v1/lending/contracts",
        "fields": [
            {
                "name": "cifNumber",
                "label": "Customer CIF Number",
                "type": "TEXT",
                "required": true,
                "placeholder": "e.g. CIF-10000001...",
                "steps": ["LOOKUP_CUSTOMER"]
            },
            {
                "name": "customerName",
                "label": "Customer Name",
                "type": "TEXT",
                "readOnly": true
            },
            {
                "name": "linkedCasaAccountNumber",
                "label": "Disbursement & Repayment CASA Account",
                "type": "TEXT",
                "required": true,
                "placeholder": "Enter 16-digit CASA account...",
                "steps": ["LOOKUP_LINKED_CASA"]
            },
            {
                "name": "productCode",
                "label": "Credit Product Package",
                "type": "CODELIST_SELECT",
                "codeListType": "LOAN_PRODUCT",
                "defaultValue": "LN_CONSUMER_VND",
                "required": true
            },
            {
                "name": "principalAmount",
                "label": "Requested Principal (VND)",
                "type": "CURRENCY",
                "required": true,
                "placeholder": "50,000,000.0000",
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "termMonths",
                "label": "Loan Term (Months)",
                "type": "NUMBER",
                "defaultValue": 12,
                "required": true,
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "interestMargin",
                "label": "Agreed Margin Rate (e.g. 0.0250)",
                "type": "NUMBER",
                "defaultValue": 0.0250,
                "steps": ["PREVIEW_LOAN_EMI"]
            },
            {
                "name": "monthlyEstimatedEmi",
                "label": "Estimated Monthly Payment (EMI)",
                "type": "CURRENCY",
                "readOnly": true
            }
        ]
    }'
WHERE form_code = 'FORM_LOAN_ORIGINATION';
