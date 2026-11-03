-- ============================================================================
-- V6: Rename PRODUCT_CATALOG to DPT_PRODUCT_CATALOG
-- Separates Deposit Product Catalog (DPT) from Lending/Credit Product Catalog (CREDIT).
-- ============================================================================

ALTER TABLE PRODUCT_CATALOG RENAME TO DPT_PRODUCT_CATALOG;

ALTER TABLE DPT_PRODUCT_CATALOG RENAME CONSTRAINT PK_PRODUCT_CATALOG TO PK_DPT_PRODUCT_CATALOG;

ALTER TABLE ACCOUNT RENAME CONSTRAINT FK_ACCT_PRODUCT TO FK_ACCT_DPT_PRODUCT;
