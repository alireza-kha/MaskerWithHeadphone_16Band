package com.masker.app.tinnitus

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.masker.app.R
import com.masker.app.audiogram.PersianDateUtils
import com.masker.app.storage.MaskerStorage
import com.masker.app.ui.MessageDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * یک اندازه‌گیری «بلندی صدای وزوز» با تطبیق نسبت به سطح صدای ماسکر در حال پخش: کاربر ولوم هر
 * گوش را (بر حسب دسی‌بلِ نسبی، صفر = هم‌سطح با صدای فعلی ماسکر) کم یا زیاد می‌کند تا با شدت
 * وزوز گوش خودش یکی شود. عدد ثبت‌شده یک سنجش نسبی و شخصی است، نه معادل dB HL بالینی.
 */
data class TinnitusLoudnessRecord(
    val timestampMillis: Long,
    val rightDb: Float,
    val leftDb: Float
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestampMillis)
        obj.put("right", rightDb)
        obj.put("left", leftDb)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): TinnitusLoudnessRecord {
            return TinnitusLoudnessRecord(
                timestampMillis = obj.optLong("timestamp", System.currentTimeMillis()),
                rightDb = obj.optDouble("right", 0.0).toFloat(),
                leftDb = obj.optDouble("left", 0.0).toFloat()
            )
        }
    }
}

/**
 * تاریخچه اندازه‌گیری‌های «بلندی صدای وزوز»، به‌صورت یک فایل JSON در
 * Documents/Masker/History (کنار سوابق اودیوگرام و پلی‌لیست).
 */
object TinnitusLoudnessStorage {
    private const val FILE_NAME = "tinnitus_loudness_history.json"

    fun saveRecord(context: Context, record: TinnitusLoudnessRecord) {
        val all = loadAllRecords(context).toMutableList()
        all.add(record)
        saveAll(context, all)
    }

    fun loadAllRecords(context: Context): List<TinnitusLoudnessRecord> {
        val file = storageFile(context)
        val json = try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<TinnitusLoudnessRecord>()
            for (i in 0 until arr.length()) {
                list.add(TinnitusLoudnessRecord.fromJson(arr.getJSONObject(i)))
            }
            list.sortedBy { it.timestampMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, records: List<TinnitusLoudnessRecord>) {
        val arr = JSONArray()
        for (r in records) arr.put(r.toJson())
        try {
            storageFile(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun storageFile(context: Context): File = File(MaskerStorage.historyDir(context), FILE_NAME)
}

/**
 * تولید یک فایل اکسل واقعی (.xlsx) از تاریخچه اندازه‌گیری «بلندی صدای وزوز»، بدون نیاز به هیچ
 * کتابخانه جانبی — فرمت xlsx در واقع یک بایگانی ZIP از چند فایل XML ساده (استاندارد
 * OOXML/SpreadsheetML) است که این‌جا با java.util.zip به‌صورت دستی ساخته می‌شود.
 */
object TinnitusLoudnessExcelExporter {

    /** ساخت فایل xlsx در Documents/Masker/Reports و بازگرداندن مسیر آن (یا null در صورت خطا) */
    fun export(activity: Activity, records: List<TinnitusLoudnessRecord>): File? {
        val outDir = MaskerStorage.reportsDir(activity)
        val fileName = "tinnitus_loudness_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".xlsx"
        val outFile = File(outDir, fileName)

        return try {
            ZipOutputStream(outFile.outputStream()).use { zip ->
                writeEntry(zip, "[Content_Types].xml", contentTypesXml())
                writeEntry(zip, "_rels/.rels", rootRelsXml())
                writeEntry(zip, "xl/workbook.xml", workbookXml())
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(records))
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun exportAndShare(activity: Activity, records: List<TinnitusLoudnessRecord>) {
        if (records.isEmpty()) {
            MessageDialog.show(activity, R.string.tinnitus_loudness_report_empty)
            return
        }
        val file = export(activity, records)
        if (file == null) {
            MessageDialog.show(activity, R.string.save_failed)
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)))
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets>
        <sheet name="TinnitusLoudness" sheetId="1" r:id="rId1"/>
        </sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun sheetXml(records: List<TinnitusLoudnessRecord>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")

        sb.append(headerRow())

        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        records.forEachIndexed { index, record ->
            val rowNum = index + 2
            val date = Date(record.timestampMillis)
            val jalali = PersianDateUtils.toJalaliString(date)
            val gregorian = PersianDateUtils.toGregorianString(date)
            val time = timeFormat.format(date)
            sb.append("<row r=\"$rowNum\">")
            sb.append(inlineStringCell("A$rowNum", jalali))
            sb.append(inlineStringCell("B$rowNum", gregorian))
            sb.append(inlineStringCell("C$rowNum", time))
            sb.append(numberCell("D$rowNum", record.rightDb))
            sb.append(numberCell("E$rowNum", record.leftDb))
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun headerRow(): String {
        val sb = StringBuilder("<row r=\"1\">")
        sb.append(inlineStringCell("A1", "تاریخ شمسی"))
        sb.append(inlineStringCell("B1", "تاریخ میلادی"))
        sb.append(inlineStringCell("C1", "ساعت"))
        sb.append(inlineStringCell("D1", "گوش راست (dB نسبی)"))
        sb.append(inlineStringCell("E1", "گوش چپ (dB نسبی)"))
        sb.append("</row>")
        return sb.toString()
    }

    private fun inlineStringCell(ref: String, value: String): String {
        return "<c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>"
    }

    private fun numberCell(ref: String, value: Float): String {
        return "<c r=\"$ref\"><v>${value}</v></c>"
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
