/**
 * این فایل کد جاوااسکریپت نیست که در اپلیکیشن اندروید کامپایل شود؛ این کدی است که باید
 * در Google Apps Script (متصل به یک Google Sheet) قرار بگیرد. برنامه ماسکر گزارش‌ها را
 * با یک درخواست HTTP POST به آدرس Web App این اسکریپت ارسال می‌کند.
 *
 * ===================== نحوه راه‌اندازی (یک‌بار) =====================
 * ۱. یک Google Sheet جدید بسازید (sheets.google.com → Blank spreadsheet).
 * ۲. از منو: Extensions → Apps Script.
 * ۳. تمام کد پیش‌فرض را پاک کنید و کد زیر را جای‌گذاری کنید.
 * ۴. از منوی بالا: Deploy → New deployment.
 *    - روی چرخ‌دنده کنار "Select type" بزنید و "Web app" را انتخاب کنید.
 *    - Execute as: Me
 *    - Who has access: Anyone
 *    - روی Deploy بزنید و اجازه دسترسی (Authorize) بدهید.
 * ۵. آدرس (URL) نمایش داده‌شده را کپی کنید (شبیه:
 *    https://script.google.com/macros/s/XXXXXXXXXXXXXXXX/exec)
 * ۶. این آدرس را در فایل local.properties پروژه اندروید، جلوی کلید SHEETS_WEBHOOK_URL
 *    قرار دهید (به README.md پروژه مراجعه کنید).
 * ۷. هر بار کد این اسکریپت را تغییر دادید، باید دوباره از Deploy → Manage deployments
 *    یک نسخه جدید منتشر کنید تا تغییرات اعمال شود.
 * =====================================================================
 */

function doPost(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();

  // اگر هنوز هدر ستون‌ها نوشته نشده، یک‌بار اضافه می‌شود
  if (sheet.getLastRow() === 0) {
    sheet.appendRow([
      'تاریخ ثبت (سرور)',
      'نام بیمار',
      'سن',
      'نمره وزوز گوش چپ (0-10)',
      'نمره وزوز گوش راست (0-10)',
      'اودیوگرام (JSON)',
      'تنظیمات ماسکر نویزی (JSON)',
      'تنظیمات ماسکر تونال (JSON)',
      'زمان ثبت در گوشی (Unix ms)'
    ]);
  }

  var data = JSON.parse(e.postData.contents);

  sheet.appendRow([
    new Date(),
    data.patientName || '',
    data.patientAge || '',
    data.leftTinnitusScore === null || data.leftTinnitusScore === undefined ? '' : data.leftTinnitusScore,
    data.rightTinnitusScore === null || data.rightTinnitusScore === undefined ? '' : data.rightTinnitusScore,
    data.audiogram ? JSON.stringify(data.audiogram) : '',
    data.noiseMaskerSettings ? JSON.stringify(data.noiseMaskerSettings) : '',
    data.tonalMaskerSettings ? JSON.stringify(data.tonalMaskerSettings) : '',
    data.timestamp || ''
  ]);

  return ContentService
    .createTextOutput(JSON.stringify({ status: 'success' }))
    .setMimeType(ContentService.MimeType.JSON);
}
