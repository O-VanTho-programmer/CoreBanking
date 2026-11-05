# Core Banking Architecture & Accounting Rules

## I. Financial Core Invariants (Bất Biến Tài Chính Bắt Buộc)
1. **Invariant 1: Double-Entry Equilibrium (Cân đối kép Nợ = Có)**:
   Mọi giao dịch tài chính phải có $\sum \text{Debit} = \sum \text{Credit}$. Thẩm định qua `PostingEngineService`.
2. **Invariant 2: Balance Integrity (Toàn vẹn số dư)**:
   `available_balance = ledger_balance - hold_balance`. `hold_balance >= 0`.
3. **Invariant 3: Deterministic Row Locking (Khóa bi quan chống Deadlock)**:
   Khóa tài khoản theo thứ tự xác định (`ORDER BY account_id ASC FOR UPDATE`) để triệt tiêu hoàn toàn `ORA-00060`.
4. **Invariant 4: Working Date Decoupling (Độc lập ngày làm việc)**:
   Hạch toán theo ngày làm việc do `WorkingDateService` quản lý; chặn giao dịch khi hệ thống đang trong trạng thái EOD Cutoff (`is_system_cutoff = 'Y'`).
5. **Invariant 5: Two-Tier Composite Idempotency**:
   Sử dụng composite key `channelId + ":" + idempotencyKey` qua Redis (Hot) và `TRX_JOURNAL_ACTIVE` / `DONE` (Cold).
6. **Invariant 6: Dynamic Status Validation via CodeList**:
   Toàn bộ mã trạng thái (`ACCT_STATUS`, `LOAN_STATUS`, `LOAN_SCHED_STATUS`, `REPAYMENT_TYPE`, `TRX_CODE`) phải được kiểm tra qua `CodeListService`.

---

## II. Lending & Provisioning Accounting Standard (Chuẩn Mực Kế Toán Tín Dụng & Dự Phòng Rủi Ro)

### Quy Tắc Hoàn Nhập Dự Phòng Rủi Ro Tín Dụng (Provision Release / Reversal)
Khi một khoản vay tín dụng được thanh toán, tất toán hoàn tất (`PAID_OFF`) hoặc được nâng hạng nhóm nợ:

#### 1. Định Khoản Chuẩn Bắt Buộc:
* **Ghi Nợ (DR)**: `PROV_DR_RVS` (TK `2191` Quỹ dự phòng chung / `2192` Quỹ dự phòng cụ thể) $\longrightarrow$ Giảm quỹ dự phòng rủi ro trên Bảng cân đối tài sản.
* **Ghi Có (CR)**: `PROV_CR_RVS` (TK `7911` Thu nhập từ hoàn nhập dự phòng rủi ro tín dụng) $\longrightarrow$ Ghi nhận doanh thu trên Báo cáo kết quả hoạt động kinh doanh (P&L).
* ⚠️ **CẤM TUYỆT ĐỐI**: Không được ghi CÓ trực tiếp vào các tài khoản Chi phí `8821` / `8822` khi hoàn nhập nợ đã tất toán.

#### 2. Ba Lý Do Cốt Lõi Bắt Buộc Tuân Thủ:
1. **Nguyên tắc Kỳ kế toán (Periodicity Principle) & Đóng sổ P&L**:
   Khoản nợ được trích lập dự phòng ở các kỳ kế toán trước (tháng trước, quý trước, năm trước). Chi phí `88xx` của kỳ đó đã được kết chuyển đóng sổ P&L vào Lợi nhuận giữ lại (`6911/911`). Khi sang kỳ này khách hàng tất toán, việc hoàn nhập phải được ghi nhận là một khoản **Thu nhập của kỳ này**, không được ghi âm chi phí của kỳ này.
2. **Quy định Chế độ Kế toán Ngân hàng Nhà nước (QĐ 479/2004/QĐ-NHNN & Thông tư 10/2014/TT-NHNN)**:
   Hệ thống tài khoản các Tổ chức tín dụng quy định riêng **Tài khoản Loại 79 (Thu nhập khác)**, cụ thể là **TK `7911` (Thu từ hoàn nhập dự phòng rủi ro tín dụng)** để theo dõi các khoản hoàn nhập dự phòng.
3. **Nguyên tắc Minh bạch & Chống cấn trừ (No Netting-Off Principle)**:
   Kiểm toán độc lập (Big 4) và Ban Điều hành yêu cầu tách bạch rõ ràng giữa:
   - Tổng chi phí rủi ro phát sinh trong kỳ (Tổng phát sinh Nợ của `88xx`).
   - Tổng thu hồi từ các khoản nợ đã xử lý rủi ro / hoàn nhập (Tổng phát sinh Có của `79xx`).
   Nếu ghi Có trực tiếp vào `88xx`, hai chỉ tiêu này sẽ bị triệt tiêu (net off), che giấu quy mô rủi ro thực tế của ngân hàng.

---

## III. Bank Account Numbering Standard (Chuẩn Số Tài Khoản Ngân Hàng Thuần Số)
* Mọi số tài khoản mở mới trong hệ thống (cả Deposit `ACCOUNT` và Credit `LOAN_ACCOUNT`) phải tuân thủ chuẩn **16 chữ số thuần túy (Pure Numeric String)**:
  $$\mathbf{AccountNumber} = \mathbf{BranchCode} \ (3\text{ số}) + \mathbf{GL\ Code} \ (4\text{ số}) + \mathbf{Sequence} \ (9\text{ số})$$
* **Ví dụ**:
  - Tiền gửi (CASA): `0014211000001001` (Chi nhánh 001, GL 4211)
  - Tiền vay (Credit): `0012111000000101` (Chi nhánh 001, GL 2111)
* **Quy tắc**: Tuyệt đối **không thêm tiền tố chữ** (như `"LN"`) vào số tài khoản khi mã GL đã phân biệt rõ ràng bản chất kế toán (Asset vs. Liability).
* Xem chi tiết tại: [account_numbering_rules.md](file:///.agents/rules/account_numbering_rules.md).
