-- ============================================================================
-- V19: Seed Server-Driven Actions for Transaction Forms
-- Enables explicit 'actions' array in schema_payload for:
--   - FORM_CASH_DEP (Submit)
--   - FORM_CASH_WDR (Submit)
--   - FORM_TRF_INTERNAL (Calculate Fee, Submit)
--   - FORM_LOAN_ORIGINATION (Submit)
-- ============================================================================

-- 1. FORM_CASH_DEP
UPDATE FORM
SET form_title = 'Cash Deposit',
    schema_payload = TO_CLOB('{
  "formCode": "FORM_CASH_DEP",
  "trxCode": "CASH_DEP",
  "title": "Cash Deposit",
  "submitEndpoint": "/transactions/deposit",
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
  ],
  "actions": [
    {
      "code": "BTN_SUBMIT_CASH_DEP",
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/transactions/deposit"
    }
  ]
}')
WHERE form_code = 'FORM_CASH_DEP';

-- 2. FORM_CASH_WDR
UPDATE FORM
SET form_title = 'Cash Withdrawal',
    schema_payload = TO_CLOB('{
  "formCode": "FORM_CASH_WDR",
  "trxCode": "CASH_WDR",
  "title": "Cash Withdrawal",
  "submitEndpoint": "/transactions/withdraw",
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
  ],
  "actions": [
    {
      "code": "BTN_SUBMIT_CASH_WDR",
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/transactions/withdraw"
    }
  ]
}')
WHERE form_code = 'FORM_CASH_WDR';

-- 3. FORM_TRF_INTERNAL
UPDATE FORM
SET form_title = 'Internal Fund Transfer',
    schema_payload = TO_CLOB('{
  "formCode": "FORM_TRF_INTERNAL",
  "trxCode": "TRF_INTERNAL",
  "title": "Internal Fund Transfer",
  "submitEndpoint": "/transactions/transfer",
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
  ],
  "actions": [
    {
      "code": "BTN_CALC_FEE",
      "label": "Calculate Fee",
      "style": "secondary",
      "actionType": "WORKFLOW_STEP",
      "stepCode": "CALCULATE_TRANSFER_FEE"
    },
    {
      "code": "BTN_SUBMIT_TRF",
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/transactions/transfer"
    }
  ]
}')
WHERE form_code = 'FORM_TRF_INTERNAL';

-- 4. FORM_LOAN_ORIGINATION
UPDATE FORM
SET form_title = 'Loan Contract Origination',
    schema_payload = TO_CLOB('{
  "formCode": "FORM_LOAN_ORIGINATION",
  "trxCode": "LOAN_CREATE",
  "title": "Loan Contract Origination",
  "submitEndpoint": "/loans/contract",
  "fields": [
    {
      "name": "customerId",
      "label": "Customer CIF Number",
      "type": "TEXT",
      "required": true,
      "placeholder": "Enter CIF number...",
      "steps": ["LOOKUP_CUSTOMER"]
    },
    {
      "name": "customerName",
      "label": "Customer Full Name",
      "type": "TEXT",
      "readOnly": true
    },
    {
      "name": "productCode",
      "label": "Credit Product Code",
      "type": "CODELIST_SELECT",
      "codeListType": "CRD_PRODUCT",
      "defaultValue": "CRD_CONSUMER_VND",
      "required": true
    },
    {
      "name": "principalAmount",
      "label": "Principal Loan Amount (VND)",
      "type": "CURRENCY",
      "required": true,
      "placeholder": "0.0000"
    },
    {
      "name": "termMonths",
      "label": "Term (Months)",
      "type": "NUMBER",
      "required": true,
      "defaultValue": 12
    },
    {
      "name": "interestRate",
      "label": "Annual Interest Rate (Dec)",
      "type": "NUMBER",
      "required": true,
      "defaultValue": 0.12
    },
    {
      "name": "repaymentType",
      "label": "Repayment Schedule",
      "type": "SELECT",
      "defaultValue": "EQUAL_INSTALLMENT",
      "options": [
        { "value": "EQUAL_INSTALLMENT", "label": "Equal Installment (Annuity)" },
        { "value": "BALLOON", "label": "Bullet / Balloon at Maturity" }
      ]
    },
    {
      "name": "linkedCasaAccountNumber",
      "label": "Disbursement CASA Account",
      "type": "TEXT",
      "required": true,
      "placeholder": "Account to disburse funds...",
      "steps": ["LOOKUP_ACCOUNT"]
    }
  ],
  "actions": [
    {
      "code": "BTN_SUBMIT_LOAN",
      "label": "Submit",
      "style": "primary",
      "actionType": "SUBMIT_TRX",
      "endpoint": "/loans/contract"
    }
  ]
}')
WHERE form_code = 'FORM_LOAN_ORIGINATION';
