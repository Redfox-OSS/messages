package dev.redfox.messages.core.domain

import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.MessageCategory

/**
 * On-device inbox categoriser (Google Messages India style). Pure heuristics over the sender
 * and the latest message text; ML Kit entity extraction can refine this later (PLAN.md §2).
 *
 * Order matters: an OTP from a bank is an OTP, not a transaction, and a person texting a
 * code is still an OTP; everything else from a phone number is Personal.
 */
object MessageCategorizer {

    private val otpKeywords = Regex(
        """\b(otp|one[\s-]?time\s+(pass(word|code)?|pin)|verification\s+code|security\s+code|login\s+code|""" +
            """auth(entication)?\s+code|passcode|code\s+is|is\s+your\s+(\w+\s+)?code)\b""",
        RegexOption.IGNORE_CASE,
    )
    // A standalone 4–8 digit run: not part of an amount like "1,250.00" (separator *between* digits).
    private val otpDigits = Regex("""(?<!\d)(?<!\d[.,])\d{4,8}(?!\d)(?![.,]\d)""")

    private val transactionKeywords = Regex(
        """\b(debited|credited|a/c|acct|account\s+\w*\s*(debited|credited)|upi|txn|transaction|spent|""" +
            """withdrawn|refund(ed)?|avl\.?\s*bal|available\s+balance|neft|imps|rtgs|emi)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val amount = Regex("""(rs\.?|inr|₹)\s?\d""", RegexOption.IGNORE_CASE)

    fun categorize(participants: List<Contact>, latestText: String): MessageCategory {
        if (isOtp(latestText)) return MessageCategory.Otp
        if (participants.size != 1 || !participants.first().isAlphanumericSender) return MessageCategory.Personal
        if (isTransaction(latestText)) return MessageCategory.Transactions
        return MessageCategory.Offers
    }

    fun isOtp(text: String): Boolean = otpKeywords.containsMatchIn(text) && otpDigits.containsMatchIn(text)

    fun isTransaction(text: String): Boolean =
        transactionKeywords.containsMatchIn(text) && amount.containsMatchIn(text)
}
