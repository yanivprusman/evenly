package com.automatelinux.evenly.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Category(val key: String, val label: String, val icon: ImageVector, val color: Color)

private val FOOD = Color(0xFFE08A3C)
private val HOME = Color(0xFF4C7A9C)
private val UTIL = Color(0xFF3E9C8F)
private val TRANS = Color(0xFF7466B8)
private val LIFE = Color(0xFFC25C84)
private val FUN = Color(0xFFD1A43A)
private val GEN = Color(0xFF7C8784)

val CATEGORIES: List<Category> = listOf(
    Category("general", "General", Icons.AutoMirrored.Outlined.ReceiptLong, GEN),
    Category("food.groceries", "Groceries", Icons.Outlined.ShoppingCart, FOOD),
    Category("food.dining", "Dining out", Icons.Outlined.Restaurant, FOOD),
    Category("food.liquor", "Liquor", Icons.Outlined.LocalBar, FOOD),
    Category("home.rent", "Rent", Icons.Outlined.House, HOME),
    Category("home.mortgage", "Mortgage", Icons.Outlined.AccountBalance, HOME),
    Category("home.household", "Household supplies", Icons.Outlined.CleaningServices, HOME),
    Category("home.furniture", "Furniture", Icons.Outlined.Chair, HOME),
    Category("home.maintenance", "Maintenance", Icons.Outlined.Build, HOME),
    Category("home.electronics", "Electronics", Icons.Outlined.Devices, HOME),
    Category("home.pets", "Pets", Icons.Outlined.Pets, HOME),
    Category("home.services", "Services", Icons.Outlined.HomeRepairService, HOME),
    Category("utilities.electricity", "Electricity", Icons.Outlined.Bolt, UTIL),
    Category("utilities.water", "Water", Icons.Outlined.WaterDrop, UTIL),
    Category("utilities.gas", "Gas", Icons.Outlined.LocalFireDepartment, UTIL),
    Category("utilities.internet", "TV / Phone / Internet", Icons.Outlined.Wifi, UTIL),
    Category("utilities.trash", "Trash", Icons.Outlined.Delete, UTIL),
    Category("utilities.cleaning", "Cleaning", Icons.Outlined.CleaningServices, UTIL),
    Category("transport.car", "Car", Icons.Outlined.DirectionsCar, TRANS),
    Category("transport.fuel", "Fuel", Icons.Outlined.LocalGasStation, TRANS),
    Category("transport.parking", "Parking", Icons.Outlined.LocalParking, TRANS),
    Category("transport.taxi", "Taxi", Icons.Outlined.LocalTaxi, TRANS),
    Category("transport.bus", "Bus / Train", Icons.Outlined.DirectionsBus, TRANS),
    Category("transport.plane", "Plane", Icons.Outlined.Flight, TRANS),
    Category("transport.hotel", "Hotel", Icons.Outlined.Hotel, TRANS),
    Category("transport.bicycle", "Bicycle", Icons.AutoMirrored.Outlined.DirectionsBike, TRANS),
    Category("life.childcare", "Childcare", Icons.Outlined.ChildCare, LIFE),
    Category("life.clothing", "Clothing", Icons.Outlined.Checkroom, LIFE),
    Category("life.education", "Education", Icons.Outlined.School, LIFE),
    Category("life.gifts", "Gifts", Icons.Outlined.CardGiftcard, LIFE),
    Category("life.insurance", "Insurance", Icons.Outlined.Shield, LIFE),
    Category("life.medical", "Medical", Icons.Outlined.LocalHospital, LIFE),
    Category("life.taxes", "Taxes", Icons.Outlined.AccountBalance, LIFE),
    Category("fun.games", "Games", Icons.Outlined.SportsEsports, FUN),
    Category("fun.movies", "Movies", Icons.Outlined.Movie, FUN),
    Category("fun.music", "Music", Icons.Outlined.MusicNote, FUN),
    Category("fun.sports", "Sports", Icons.Outlined.SportsSoccer, FUN),
    Category("fun.other", "Entertainment", Icons.Outlined.Celebration, FUN),
)

val PAYMENT_CATEGORY = Category("payment", "Payment", Icons.Outlined.Payments, Color(0xFF1F9D6B))

private val BY_KEY = CATEGORIES.associateBy { it.key }

fun category(key: String): Category = BY_KEY[key] ?: BY_KEY.getValue("general")

val CATEGORY_GROUPS = listOf(
    "Food and drink" to "food.", "Home" to "home.", "Utilities" to "utilities.",
    "Transportation" to "transport.", "Life" to "life.", "Entertainment" to "fun.",
)

// Keyword → category. Checked in order, substring match on the lower-cased description, so
// longer / more specific keys come first ("כביש 6" before anything with "כביש").
private val KEYWORDS: List<Pair<String, String>> = listOf(
    "כביש 6" to "transport.car", "כביש6" to "transport.car", "toll" to "transport.car",
    "שופרסל" to "food.groceries", "רמי לוי" to "food.groceries", "ויקטורי" to "food.groceries",
    "יוחננוף" to "food.groceries", "מגה" to "food.groceries", "טיב טעם" to "food.groceries",
    "אושר עד" to "food.groceries", "סופר" to "food.groceries", "ירקות" to "food.groceries",
    "פירות" to "food.groceries", "מכולת" to "food.groceries", "קניות" to "food.groceries",
    "grocer" to "food.groceries", "supermarket" to "food.groceries", "market" to "food.groceries",
    "ארנונה" to "life.taxes", "מס " to "life.taxes", "tax" to "life.taxes",
    "חשמל" to "utilities.electricity", "electric" to "utilities.electricity",
    "מים" to "utilities.water", "water" to "utilities.water",
    "גז" to "utilities.gas", "gas bill" to "utilities.gas",
    "דלק" to "transport.fuel", "סונול" to "transport.fuel", "פז " to "transport.fuel",
    "fuel" to "transport.fuel", "petrol" to "transport.fuel",
    "אינטרנט" to "utilities.internet", "בזק" to "utilities.internet", "הוט" to "utilities.internet",
    "סלקום" to "utilities.internet", "פרטנר" to "utilities.internet", "internet" to "utilities.internet",
    "wifi" to "utilities.internet", "netflix" to "fun.movies",
    "קפה" to "food.dining", "מסעדה" to "food.dining", "פיצה" to "food.dining", "סושי" to "food.dining",
    "המבורגר" to "food.dining", "וולט" to "food.dining", "wolt" to "food.dining",
    "restaurant" to "food.dining", "coffee" to "food.dining", "cafe" to "food.dining", "pizza" to "food.dining",
    "בירה" to "food.liquor", "יין" to "food.liquor", "wine" to "food.liquor", "beer" to "food.liquor",
    "שכירות" to "home.rent", "שכר דירה" to "home.rent", "rent" to "home.rent",
    "משכנתא" to "home.mortgage", "mortgage" to "home.mortgage",
    "ועד בית" to "home.services", "ניקיון" to "utilities.cleaning", "עוזרת" to "utilities.cleaning",
    "cleaning" to "utilities.cleaning",
    "רהיט" to "home.furniture", "איקאה" to "home.furniture", "ikea" to "home.furniture",
    "תיקון" to "home.maintenance", "אינסטלטור" to "home.maintenance", "repair" to "home.maintenance",
    "וטרינר" to "home.pets", "כלב" to "home.pets", "חתול" to "home.pets", "vet" to "home.pets",
    "חניה" to "transport.parking", "חנייה" to "transport.parking", "parking" to "transport.parking",
    "מונית" to "transport.taxi", "גט" to "transport.taxi", "taxi" to "transport.taxi", "uber" to "transport.taxi",
    "רכבת" to "transport.bus", "אוטובוס" to "transport.bus", "רב קו" to "transport.bus",
    "train" to "transport.bus", "bus" to "transport.bus",
    "טיסה" to "transport.plane", "flight" to "transport.plane",
    "מלון" to "transport.hotel", "צימר" to "transport.hotel", "hotel" to "transport.hotel", "airbnb" to "transport.hotel",
    "טסט" to "transport.car", "מוסך" to "transport.car", "רכב" to "transport.car", "car" to "transport.car",
    "ביטוח" to "life.insurance", "insurance" to "life.insurance",
    "רופא" to "life.medical", "תרופ" to "life.medical", "סופר-פארם" to "life.medical", "pharm" to "life.medical",
    "doctor" to "life.medical",
    "גן" to "life.childcare", "מעון" to "life.childcare", "בייביסיטר" to "life.childcare",
    "בגדים" to "life.clothing", "נעליים" to "life.clothing", "clothes" to "life.clothing",
    "מתנה" to "life.gifts", "gift" to "life.gifts",
    "קורס" to "life.education", "ספרים" to "life.education", "course" to "life.education",
    "סרט" to "fun.movies", "קולנוע" to "fun.movies", "movie" to "fun.movies", "cinema" to "fun.movies",
    "הופעה" to "fun.music", "concert" to "fun.music", "spotify" to "fun.music",
    "חדר כושר" to "fun.sports", "gym" to "fun.sports",
)

/** Suggested category for a description, or null when nothing matches. */
fun suggestCategory(description: String): String? {
    val d = " " + description.lowercase().trim() + " "
    if (d.isBlank()) return null
    return KEYWORDS.firstOrNull { (k, _) -> d.contains(k) }?.second
}
