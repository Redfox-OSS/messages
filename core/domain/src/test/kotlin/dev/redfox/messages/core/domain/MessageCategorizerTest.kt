package dev.redfox.messages.core.domain

import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.MessageCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCategorizerTest {

    private val bank = listOf(Contact("VM-HDFCBK"))
    private val shop = listOf(Contact("JD-MYNTRA"))
    private val person = listOf(Contact("+919876543210"))

    private fun categorize(sender: List<Contact>, text: String) = MessageCategorizer.categorize(sender, text)

    @Test
    fun `otp from a service`() = assertEquals(
        MessageCategory.Otp,
        categorize(bank, "482913 is your OTP for login. Do not share it with anyone."),
    )

    @Test
    fun `otp with code-is phrasing`() = assertEquals(
        MessageCategory.Otp,
        categorize(shop, "Your verification code is 7310. Valid for 10 minutes."),
    )

    @Test
    fun `bank debit is a transaction`() = assertEquals(
        MessageCategory.Transactions,
        categorize(bank, "Rs.1,250.00 debited from A/c XX1234 on 16-10-26 via UPI. Avl Bal Rs.20,431.12"),
    )

    @Test
    fun `rupee symbol credit is a transaction`() = assertEquals(
        MessageCategory.Transactions,
        categorize(bank, "₹5000 credited to your account XX9876 by NEFT."),
    )

    @Test
    fun `promotional sender is an offer`() = assertEquals(
        MessageCategory.Offers,
        categorize(shop, "Big Festive Sale! Flat 60% off on top brands. Shop now."),
    )

    @Test
    fun `a person is personal even when mentioning money`() = assertEquals(
        MessageCategory.Personal,
        categorize(person, "I paid Rs 500 for the tickets, send it on UPI"),
    )

    @Test
    fun `group threads are personal`() = assertEquals(
        MessageCategory.Personal,
        categorize(listOf(Contact("+911111111111"), Contact("+912222222222")), "See you at 7"),
    )

    @Test
    fun `amount without transaction words is not a transaction`() = assertEquals(
        MessageCategory.Offers,
        categorize(shop, "Get products starting at Rs 99 this weekend"),
    )

    @Test
    fun `a year is not an otp`() = assertEquals(
        MessageCategory.Personal,
        categorize(person, "Happy new year 2027!"),
    )
}
