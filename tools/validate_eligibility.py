#!/usr/bin/env python3
"""اعتبارسنجی ساختار راهنمای انتخاب و شماره صفحات PDFها."""
import json
import pathlib

from pypdf import PdfReader

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"

data = json.loads((ASSETS / "eligibility-index.json").read_text(encoding="utf-8"))
level_ids = {item["id"] for item in data["levels"]}
count = 0

assert len(data["fields"]) == 12, "تعداد حوزه‌ها باید ۱۲ باشد."

for field in data["fields"]:
    pdf_path = ASSETS / "pdfs" / f"{field['id']}.pdf"
    assert pdf_path.exists(), f"فایل PDF یافت نشد: {pdf_path.name}"
    page_count = len(PdfReader(str(pdf_path)).pages)
    for item in field["items"]:
        count += 1
        assert item["levels"], f"مقطع گرایش مشخص نشده: {item['title']}"
        assert set(item["levels"]) <= level_ids, f"کد مقطع نامعتبر: {item['title']}"
        assert 1 <= item["page"] <= page_count, f"شماره صفحه نامعتبر: {item['title']}"

assert count == 77, f"تعداد گرایش‌ها باید ۷۷ باشد؛ مقدار فعلی: {count}"
print(f"OK: 12 fields, {count} orientations")
