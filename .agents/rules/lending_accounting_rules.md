---
trigger: "lending, provision, hoàn nhập, dự phòng, 2191, 2192, 8821, 8822, 7911, LOAN_PROVISION, LOAN_REPAY"
description: "Quy chuẩn hạch toán trích lập và hoàn nhập dự phòng rủi ro tín dụng theo chuẩn Core Banking NHNN"
---

# Chuẩn Mực Kế Toán Trích Lập & Hoàn Nhập Dự Phòng Tín Dụng

## 1. Trích Lập Dự Phòng (Provisioning)
- **Dự phòng chung (0.75%)**: Nợ `8822` (Chi phí dự phòng chung) / Có `2191` (Quỹ dự phòng chung).
- **Dự phòng cụ thể (0% - 100%)**: Nợ `8821` (Chi phí dự phòng cụ thể) / Có `2192` (Quỹ dự phòng cụ thể).

## 2. Hoàn Nhập Dự Phòng Khi Tất Toán / Nâng Hạng Nợ (Provision Release)
- **Bắt buộc hạch toán**:
  - **Nợ**: `PROV_DR_RVS` (TK `2191` hoặc `2192` - Giảm số dư Quỹ dự phòng)
  - **Có**: `PROV_CR_RVS` (TK `7911` - Thu nhập từ hoàn nhập dự phòng rủi ro tín dụng)
- **CẤM**: Không ghi Có trực tiếp vào TK Chi phí `8821` / `8822` khi hoàn nhập nợ đã tất toán.

## 3. Ba Lý Do Cốt Lõi Phải Tuân Thủ:
1. **Nguyên tắc Kỳ kế toán (Periodicity Principle)**: Dự phòng đã trích ở kỳ trước (đã đóng sổ P&L), hoàn nhập kỳ này phải là **Thu nhập của kỳ này** (TK 7911), không được ghi âm chi phí kỳ này.
2. **Quy định Ngân hàng Nhà nước (QĐ 479 & TT 10)**: Bắt buộc dùng TK `7911` cho nghiệp vụ thu hoàn nhập dự phòng rủi ro tín dụng.
3. **Nguyên tắc Chống cấn trừ (No Netting-Off)**: Minh bạch cho Kiểm toán (Big 4) giữa Tổng chi phí rủi ro phát sinh (`88xx`) và Tổng thu hồi nợ/hoàn nhập (`79xx`).
