package ir.jrweb.banksms

import java.text.DecimalFormat
import java.util.regex.Pattern

object BankSmsParser {

    data class ParsedTransaction(
        val type: String,
        val formattedAmount: String
    )

    fun parse(sender: String, rawBody: String): ParsedTransaction? {
        val body = normalizePersian(rawBody)
        val normSender = normalizePersian(sender)

        if (!isBankSms(normSender, body)) return null
        val type = detectTransactionType(body) ?: return null
        val amount = extractAmountInToman(body) ?: return null

        return ParsedTransaction(type, amount)
    }

    private fun normalizePersian(input: String): String {
        return input
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("٬", ",")
            .replace("،", ",")
            .replace("−", "-")
            .replace("–", "-")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8")
            .replace("۹", "9")
    }

    private fun isBankSms(sender: String, body: String): Boolean {
        val otpKeywords = listOf("رمز", "کد تایید", "پویا", "محرمانه", "code", "otp", "ورود به")
        if (otpKeywords.any { body.contains(it, ignoreCase = true) }) return false

        val cleanSender = sender.replace("+98", "0").replace(" ", "")
        if (cleanSender.matches(Regex("^09\\d{9}$"))) return false

        val bankKeywords = listOf(
            "بانک", "موجودی", "مانده", "حساب", "کارت", "واریز", "برداشت",
            "پرید", "نشست", "ساتنا", "پایا", "شبا", "تراکنش", "ریال", "تومان",
            "خرید", "پرداخت", "انتقال", "کسر", "بدهکار", "بستانکار", "پوز", "pos",
            "ملت", "ملی", "صادرات", "تجارت", "سپه", "کشاورزی", "مسکن",
            "سامان", "پاسارگاد", "پارسیان", "اقتصاد نوین", "شهر", "دی",
            "آینده", "سینا", "رفاه", "گردشگری", "ایران زمین", "پست بانک",
            "بلو", "blu", "ویپاد", "رسالت", "مهر ایران", "خاورمیانه", "سرمایه"
        )
        return bankKeywords.any { body.contains(it, ignoreCase = true) || sender.contains(it, ignoreCase = true) }
    }

    private fun detectTransactionType(body: String): String? {
        val depositKeywords = listOf(
            "واریز", "افزایش", "بستانکار", "انتقال به", "دریافت وجه",
            "واریزی", "به حسابت نشست", "نشست به حسابت", "به حساب شما نشست", "عودت", "برگشت"
        )
        val withdrawKeywords = listOf(
            "برداشت", "کسر", "بدهکار", "خرید", "انتقال از",
            "پرداخت", "کارمزد", "پرید", "از حسابت پرید", "از حساب شما پرید",
            "پوز", "pos", "قبض", "قسط", "خروج"
        )

        var firstDepositIdx = Int.MAX_VALUE
        for (kw in depositKeywords) {
            val idx = body.indexOf(kw, ignoreCase = true)
            if (idx in 0 until firstDepositIdx) firstDepositIdx = idx
        }

        var firstWithdrawIdx = Int.MAX_VALUE
        for (kw in withdrawKeywords) {
            val idx = body.indexOf(kw, ignoreCase = true)
            if (idx in 0 until firstWithdrawIdx) firstWithdrawIdx = idx
        }

        if (firstDepositIdx != Int.MAX_VALUE || firstWithdrawIdx != Int.MAX_VALUE) {
            return if (firstDepositIdx < firstWithdrawIdx) "DEPOSIT" else "WITHDRAW"
        }

        if (Pattern.compile("(?:\\+\\s*[0-9,]{3,}|[0-9,]{3,}\\s*\\+)").matcher(body).find()) {
            return "DEPOSIT"
        }
        if (Pattern.compile("(?:(?<![0-9/])-\\s*[0-9,]{3,}|[0-9,]{3,}\\s*-(?![0-9]))").matcher(body).find()) {
            return "WITHDRAW"
        }

        return null
    }

    private fun extractAmountInToman(body: String): String? {
        val cleaned = body
            .replace(Regex("\\b\\d{4}[-\\s*]+\\d{2,4}[-\\s*]+\\d{2,4}[-\\s*]+\\d{4}\\b"), " ")
            .replace(Regex("IR\\d{24}", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b\\d{2,4}/\\d{1,2}/\\d{1,2}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b"), " ")
            .replace(Regex("(پیگیری|ارجاع|شماره)\\s*:?\\s*\\d+"), " ")
            .replace(Regex("(موجودی|مانده)[^\\n\\r]*"), " ")

        val matcher = Pattern.compile("(?:مبلغ|واریز|برداشت|خرید|پرداخت|کسر|افزایش|انتقال)?\\s*:?\\s*[+-]?\\s*([0-9][0-9,\\.]{2,})\\s*[+-]?").matcher(cleaned)
        var rawNumberStr: String? = null

        while (matcher.find()) {
            val candidate = matcher.group(1)?.replace(",", "")?.replace(".", "") ?: continue
            if (candidate.length in 3..13 && !candidate.startsWith("140")) {
                rawNumberStr = candidate
                break
            }
        }

        val amount = rawNumberStr?.toLongOrNull() ?: return null
        if (amount <= 0) return null

        val isExplicitToman = body.contains("تومان") && !body.contains("ریال")
        val tomanValue = if (isExplicitToman) amount else amount / 10

        val formatter = DecimalFormat("#,###")
        return formatter.format(tomanValue)
    }
}