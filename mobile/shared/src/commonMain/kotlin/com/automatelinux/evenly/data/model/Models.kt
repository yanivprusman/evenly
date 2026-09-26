package com.automatelinux.evenly.data.model

import kotlinx.serialization.Serializable

// Mirrors /opt/dev/evenly/API.md. Money is always integer minor units + ISO-4217 code.

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val color: String = "#5B7C8D",
    val registered: Boolean = false,
    val defaultCurrency: String? = null,
)

@Serializable
data class Money(val amount: Long, val currency: String)

@Serializable
data class Share(
    val userId: Int,
    val paid: Long,
    val owed: Long,
    val input: Double? = null,
)

@Serializable
data class Comment(
    val id: Int,
    val expenseId: Int,
    val user: User,
    val body: String,
    val createdAt: String,
)

@Serializable
data class Activity(
    val id: Int,
    val actor: User,
    val type: String,
    val groupId: Int? = null,
    val expenseId: Int? = null,
    val text: String,
    val createdAt: String,
    val amountForMe: Money? = null,
)

@Serializable
data class Expense(
    val id: Int,
    val groupId: Int? = null,
    val description: String,
    val cost: Long,
    val currency: String,
    val date: String,
    val category: String = "general",
    val notes: String? = null,
    val isPayment: Boolean = false,
    val splitType: String = "equal",
    val repeat: String = "none",
    val shares: List<Share> = emptyList(),
    val receiptUrl: String? = null,
    val createdBy: Int? = null,
    val createdAt: String? = null,
    val updatedBy: Int? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
    val commentCount: Int = 0,
    // Only present on GET /api/expenses/:id
    val comments: List<Comment> = emptyList(),
    val history: List<Activity> = emptyList(),
)

/** Body for POST /api/expenses and PATCH /api/expenses/:id. */
@Serializable
data class ExpenseInput(
    val groupId: Int?,
    val description: String,
    val cost: Long,
    val currency: String,
    val date: String,
    val category: String,
    val notes: String?,
    val isPayment: Boolean,
    val splitType: String,
    val repeat: String,
    val shares: List<Share>,
)

@Serializable
data class Group(
    val id: Int,
    val name: String,
    val type: String = "other",
    val defaultCurrency: String = "ILS",
    val simplifyDebts: Boolean = true,
    val members: List<User> = emptyList(),
    val createdAt: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class Debt(val from: Int, val to: Int, val amount: Long, val currency: String)

@Serializable
data class MemberBalance(val userId: Int, val net: List<Money> = emptyList())

@Serializable
data class GroupNet(val groupId: Int? = null, val name: String, val net: List<Money> = emptyList())

@Serializable
data class GroupSummary(
    val group: Group,
    val myNet: List<Money> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val lastActivityAt: String? = null,
)

@Serializable
data class FriendSummary(
    val friend: User,
    val net: List<Money> = emptyList(),
    val byGroup: List<GroupNet> = emptyList(),
)

@Serializable
data class Dashboard(
    val me: User,
    val total: List<Money> = emptyList(),
    val groups: List<GroupSummary> = emptyList(),
    val friends: List<FriendSummary> = emptyList(),
)

@Serializable
data class GroupDetail(
    val group: Group,
    val balances: List<MemberBalance> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val myNet: List<Money> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

@Serializable
data class FriendDetail(
    val friend: User,
    val net: List<Money> = emptyList(),
    val byGroup: List<GroupNet> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

@Serializable
data class CategoryAmount(val category: String, val amount: Long)

@Serializable
data class MonthAmount(val month: String, val amount: Long)

@Serializable
data class Stats(
    val byCategory: List<CategoryAmount> = emptyList(),
    val byMonth: List<MonthAmount> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class Currency(val code: String, val symbol: String, val name: String)

// ---- request bodies ----

@Serializable
data class CreateGroupInput(
    val name: String,
    val type: String,
    val memberIds: List<Int>,
    val defaultCurrency: String? = null,
    val simplifyDebts: Boolean? = null,
)

@Serializable
data class PatchGroupInput(
    val name: String? = null,
    val type: String? = null,
    val defaultCurrency: String? = null,
    val simplifyDebts: Boolean? = null,
    val archived: Boolean? = null,
)

@Serializable
data class AddMemberInput(
    val userId: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class FriendInput(val name: String, val email: String? = null, val phone: String? = null)

@Serializable
data class PatchMeInput(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val defaultCurrency: String? = null,
)

@Serializable
data class CommentInput(val body: String)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class ErrorResponse(val error: String)
