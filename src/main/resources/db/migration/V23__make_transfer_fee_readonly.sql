-- Migration V23: Set feeAmount as readOnly in FORM_TRF_INTERNAL schema_payload

UPDATE FORM
SET schema_payload = TO_CLOB('{
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
      "defaultValue": 0,
      "readOnly": true
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
