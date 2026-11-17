package com.budget.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budget.app.R
import com.budget.app.data.BillIconKey

/** Phosphor Icons (MIT), bundled as vector drawables: Regular, plus Fill for active states. */
enum class Ph(@param:DrawableRes val res: Int) {
    HOUSE(R.drawable.ph_house), HOUSE_FILL(R.drawable.ph_house_fill),
    CALENDAR(R.drawable.ph_calendar_blank), CALENDAR_FILL(R.drawable.ph_calendar_blank_fill),
    CHART(R.drawable.ph_chart_line), CHART_FILL(R.drawable.ph_chart_line_fill),
    LIST(R.drawable.ph_list), LIST_FILL(R.drawable.ph_list_fill),
    PLUS(R.drawable.ph_plus),
    CHECK_CIRCLE(R.drawable.ph_check_circle), CHECK_CIRCLE_FILL(R.drawable.ph_check_circle_fill),
    CIRCLE(R.drawable.ph_circle),
    CARET_LEFT(R.drawable.ph_caret_left), CARET_RIGHT(R.drawable.ph_caret_right), CARET_DOWN(R.drawable.ph_caret_down),
    CLOUD_CHECK(R.drawable.ph_cloud_check), CLOUD_SLASH(R.drawable.ph_cloud_slash), CLOUD_UP(R.drawable.ph_cloud_arrow_up),
    SYNC(R.drawable.ph_arrows_clockwise),
    ARROW_RIGHT(R.drawable.ph_arrow_right), PENCIL(R.drawable.ph_pencil_simple), X(R.drawable.ph_x),
    SEARCH(R.drawable.ph_magnifying_glass), RESET(R.drawable.ph_arrow_counter_clockwise),
    BACKSPACE(R.drawable.ph_backspace), CHECK(R.drawable.ph_check),
    CHART_BAR(R.drawable.ph_chart_bar), OPEN_OUT(R.drawable.ph_arrow_square_out), SEND(R.drawable.ph_paper_plane_tilt),
    CONTRAST(R.drawable.ph_circle_half), DOWNLOAD(R.drawable.ph_download_simple), SIGN_OUT(R.drawable.ph_sign_out),
    SHIELD(R.drawable.ph_shield_check), WARNING(R.drawable.ph_warning_circle), BELL(R.drawable.ph_bell),
    LIGHTNING(R.drawable.ph_lightning), DROP(R.drawable.ph_drop), WIFI(R.drawable.ph_wifi_high),
    PHONE(R.drawable.ph_device_mobile), CAR(R.drawable.ph_car), BARBELL(R.drawable.ph_barbell),
    CARD(R.drawable.ph_credit_card), CARD_FILL(R.drawable.ph_credit_card_fill),
    PIE(R.drawable.ph_chart_pie_slice), PIE_FILL(R.drawable.ph_chart_pie_slice_fill),
    LIGHTBULB(R.drawable.ph_lightbulb), TREND_UP(R.drawable.ph_trend_up),
    TV(R.drawable.ph_television_simple), GAS_PUMP(R.drawable.ph_gas_pump),
    SCHOOL(R.drawable.ph_graduation_cap), RECEIPT(R.drawable.ph_receipt), MONEY(R.drawable.ph_money),
    PIGGY(R.drawable.ph_piggy_bank), HEALTH(R.drawable.ph_first_aid_kit), CART(R.drawable.ph_shopping_cart),
    PAW(R.drawable.ph_paw_print), FIRE(R.drawable.ph_fire), TRASH(R.drawable.ph_trash),
}

@Composable
fun PhIcon(icon: Ph, contentDescription: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Icon(painterResource(icon.res), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

fun iconFor(key: BillIconKey): Ph = when (key) {
    BillIconKey.HOME -> Ph.HOUSE
    BillIconKey.ELECTRIC -> Ph.LIGHTNING
    BillIconKey.WATER -> Ph.DROP
    BillIconKey.GAS_HEAT -> Ph.FIRE
    BillIconKey.TRASH -> Ph.TRASH
    BillIconKey.INTERNET -> Ph.WIFI
    BillIconKey.PHONE -> Ph.PHONE
    BillIconKey.FUEL -> Ph.GAS_PUMP
    BillIconKey.CAR -> Ph.CAR
    BillIconKey.INSURANCE -> Ph.SHIELD
    BillIconKey.GYM -> Ph.BARBELL
    BillIconKey.STREAMING -> Ph.TV
    BillIconKey.SCHOOL -> Ph.SCHOOL
    BillIconKey.HEALTH -> Ph.HEALTH
    BillIconKey.GROCERIES -> Ph.CART
    BillIconKey.PET -> Ph.PAW
    BillIconKey.CARD -> Ph.CARD
    BillIconKey.INCOME -> Ph.MONEY
    BillIconKey.SAVINGS -> Ph.PIGGY
    BillIconKey.OTHER -> Ph.RECEIPT
}
