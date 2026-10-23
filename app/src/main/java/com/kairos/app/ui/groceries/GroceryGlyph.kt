package com.kairos.app.ui.groceries

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.R
import com.kairos.app.ui.theme.KairosThemeState

private val GLYPH_TILE = Color(0xFFE8EDF3)

private val GLYPH_DRAWABLES: Map<String, Int> = mapOf(
    "ic:napkin" to R.drawable.grocery_napkin,
    "ic:papertowel" to R.drawable.grocery_papertowel,
    "ic:waterbottle" to R.drawable.grocery_waterbottle,
    "ic:protein" to R.drawable.grocery_protein,
    "ic:sorbet" to R.drawable.grocery_sorbet,
)

/** Bundled colorful Kairos grocery PNGs the catalog emits as `kairos:<slug>`
 *  for items with no good emoji. Rendered full-color like the `ic:*` set. */
private val KAIROS_DRAWABLES: Map<String, Int> = mapOf(
    "kairos:air-freshener" to R.drawable.grocery_air_freshener,
    "kairos:allergy-medicine" to R.drawable.grocery_allergy_medicine,
    "kairos:almond-milk" to R.drawable.grocery_almond_milk,
    "kairos:asparagus" to R.drawable.grocery_asparagus,
    "kairos:baby-food" to R.drawable.grocery_baby_food,
    "kairos:baby-formula" to R.drawable.grocery_baby_formula,
    "kairos:baby-wipes" to R.drawable.grocery_baby_wipes,
    "kairos:bakery-shop" to R.drawable.grocery_bakery_shop,
    "kairos:baking-powder" to R.drawable.grocery_baking_powder,
    "kairos:baking-soda" to R.drawable.grocery_baking_soda,
    "kairos:bandages" to R.drawable.grocery_bandages,
    "kairos:batteries-pack" to R.drawable.grocery_batteries_pack,
    "kairos:bbq-sauce" to R.drawable.grocery_bbq_sauce,
    "kairos:big-box-store" to R.drawable.grocery_big_box_store,
    "kairos:black-pepper" to R.drawable.grocery_black_pepper,
    "kairos:bleach" to R.drawable.grocery_bleach,
    "kairos:block-cheese" to R.drawable.grocery_block_cheese,
    "kairos:body-wash" to R.drawable.grocery_body_wash,
    "kairos:breadcrumbs" to R.drawable.grocery_breadcrumbs,
    "kairos:brisket" to R.drawable.grocery_brisket,
    "kairos:broth" to R.drawable.grocery_broth,
    "kairos:brown-sugar" to R.drawable.grocery_brown_sugar,
    "kairos:brussels-sprouts" to R.drawable.grocery_brussels_sprouts,
    "kairos:butcher-shop" to R.drawable.grocery_butcher_shop,
    "kairos:butter-sticks" to R.drawable.grocery_butter_sticks,
    "kairos:canned-beans" to R.drawable.grocery_canned_beans,
    "kairos:canned-goods" to R.drawable.grocery_canned_goods,
    "kairos:canned-tomatoes" to R.drawable.grocery_canned_tomatoes,
    "kairos:cantaloupe" to R.drawable.grocery_cantaloupe,
    "kairos:casserole" to R.drawable.grocery_casserole,
    "kairos:cat-food" to R.drawable.grocery_cat_food,
    "kairos:cat-litter" to R.drawable.grocery_cat_litter,
    "kairos:cauliflower" to R.drawable.grocery_cauliflower,
    "kairos:celery" to R.drawable.grocery_celery,
    "kairos:cheddar" to R.drawable.grocery_cheddar,
    "kairos:chicken-breast" to R.drawable.grocery_chicken_breast,
    "kairos:chicken-thighs" to R.drawable.grocery_chicken_thighs,
    "kairos:chicken-wings" to R.drawable.grocery_chicken_wings,
    "kairos:chili-powder" to R.drawable.grocery_chili_powder,
    "kairos:chips-bag" to R.drawable.grocery_chips_bag,
    "kairos:cinnamon" to R.drawable.grocery_cinnamon,
    "kairos:cleaning-spray" to R.drawable.grocery_cleaning_spray,
    "kairos:coconut-milk" to R.drawable.grocery_coconut_milk,
    "kairos:cold-cuts" to R.drawable.grocery_cold_cuts,
    "kairos:conditioner" to R.drawable.grocery_conditioner,
    "kairos:convenience-store" to R.drawable.grocery_convenience_store,
    "kairos:cooking-spray" to R.drawable.grocery_cooking_spray,
    "kairos:cornstarch" to R.drawable.grocery_cornstarch,
    "kairos:cottage-cheese" to R.drawable.grocery_cottage_cheese,
    "kairos:cotton-swabs" to R.drawable.grocery_cotton_swabs,
    "kairos:crackers-box" to R.drawable.grocery_crackers_box,
    "kairos:cream-cheese" to R.drawable.grocery_cream_cheese,
    "kairos:creamer" to R.drawable.grocery_creamer,
    "kairos:cumin" to R.drawable.grocery_cumin,
    "kairos:deli-ham" to R.drawable.grocery_deli_ham,
    "kairos:delivery-truck" to R.drawable.grocery_delivery_truck,
    "kairos:deodorant" to R.drawable.grocery_deodorant,
    "kairos:diapers" to R.drawable.grocery_diapers,
    "kairos:dinner-rolls" to R.drawable.grocery_dinner_rolls,
    "kairos:dish-soap" to R.drawable.grocery_dish_soap,
    "kairos:dishwasher-pods" to R.drawable.grocery_dishwasher_pods,
    "kairos:dog-food" to R.drawable.grocery_dog_food,
    "kairos:dryer-sheets" to R.drawable.grocery_dryer_sheets,
    "kairos:egg-noodles" to R.drawable.grocery_egg_noodles,
    "kairos:eggs-carton" to R.drawable.grocery_eggs_carton,
    "kairos:energy-drink" to R.drawable.grocery_energy_drink,
    "kairos:english-muffins" to R.drawable.grocery_english_muffins,
    "kairos:fajitas" to R.drawable.grocery_fajitas,
    "kairos:farmers-market" to R.drawable.grocery_farmers_market,
    "kairos:feta" to R.drawable.grocery_feta,
    "kairos:filet-mignon" to R.drawable.grocery_filet_mignon,
    "kairos:first-aid" to R.drawable.grocery_first_aid,
    "kairos:floss" to R.drawable.grocery_floss,
    "kairos:flour" to R.drawable.grocery_flour,
    "kairos:foil" to R.drawable.grocery_foil,
    "kairos:frozen-dinner" to R.drawable.grocery_frozen_dinner,
    "kairos:frozen-pizza" to R.drawable.grocery_frozen_pizza,
    "kairos:frozen-veggies" to R.drawable.grocery_frozen_veggies,
    "kairos:garlic-powder" to R.drawable.grocery_garlic_powder,
    "kairos:granola-bars" to R.drawable.grocery_granola_bars,
    "kairos:green-beans" to R.drawable.grocery_green_beans,
    "kairos:green-onion" to R.drawable.grocery_green_onion,
    "kairos:ground-beef" to R.drawable.grocery_ground_beef,
    "kairos:ground-sausage" to R.drawable.grocery_ground_sausage,
    "kairos:ground-turkey" to R.drawable.grocery_ground_turkey,
    "kairos:guacamole" to R.drawable.grocery_guacamole,
    "kairos:hamburger-buns" to R.drawable.grocery_hamburger_buns,
    "kairos:hand-soap" to R.drawable.grocery_hand_soap,
    "kairos:hardware-store" to R.drawable.grocery_hardware_store,
    "kairos:honey-jar" to R.drawable.grocery_honey_jar,
    "kairos:hot-dog-buns" to R.drawable.grocery_hot_dog_buns,
    "kairos:hummus" to R.drawable.grocery_hummus,
    "kairos:ice" to R.drawable.grocery_ice,
    "kairos:italian-bread" to R.drawable.grocery_italian_bread,
    "kairos:italian-seasoning" to R.drawable.grocery_italian_seasoning,
    "kairos:jam" to R.drawable.grocery_jam,
    "kairos:ketchup" to R.drawable.grocery_ketchup,
    "kairos:lasagna" to R.drawable.grocery_lasagna,
    "kairos:laundry-detergent" to R.drawable.grocery_laundry_detergent,
    "kairos:light-bulbs" to R.drawable.grocery_light_bulbs,
    "kairos:liquor-store" to R.drawable.grocery_liquor_store,
    "kairos:lotion" to R.drawable.grocery_lotion,
    "kairos:mac-and-cheese" to R.drawable.grocery_mac_and_cheese,
    "kairos:macaroni" to R.drawable.grocery_macaroni,
    "kairos:mayo" to R.drawable.grocery_mayo,
    "kairos:meatballs" to R.drawable.grocery_meatballs,
    "kairos:meatloaf" to R.drawable.grocery_meatloaf,
    "kairos:medicine" to R.drawable.grocery_medicine,
    "kairos:mozzarella" to R.drawable.grocery_mozzarella,
    "kairos:mustard" to R.drawable.grocery_mustard,
    "kairos:napkins" to R.drawable.grocery_napkins,
    "kairos:oatmeal-canister" to R.drawable.grocery_oatmeal_canister,
    "kairos:olive-oil" to R.drawable.grocery_olive_oil,
    "kairos:olives-jar" to R.drawable.grocery_olives_jar,
    "kairos:onion-powder" to R.drawable.grocery_onion_powder,
    "kairos:online-order" to R.drawable.grocery_online_order,
    "kairos:oregano" to R.drawable.grocery_oregano,
    "kairos:pain-reliever" to R.drawable.grocery_pain_reliever,
    "kairos:pancake-mix" to R.drawable.grocery_pancake_mix,
    "kairos:paper-plates" to R.drawable.grocery_paper_plates,
    "kairos:paper-towels" to R.drawable.grocery_paper_towels,
    "kairos:paprika" to R.drawable.grocery_paprika,
    "kairos:parmesan" to R.drawable.grocery_parmesan,
    "kairos:pasta-sauce" to R.drawable.grocery_pasta_sauce,
    "kairos:peanut-butter" to R.drawable.grocery_peanut_butter,
    "kairos:penne" to R.drawable.grocery_penne,
    "kairos:pepperoni" to R.drawable.grocery_pepperoni,
    "kairos:pet-treats" to R.drawable.grocery_pet_treats,
    "kairos:pharmacy" to R.drawable.grocery_pharmacy,
    "kairos:pickles-jar" to R.drawable.grocery_pickles_jar,
    "kairos:pita-bread" to R.drawable.grocery_pita_bread,
    "kairos:plastic-cutlery" to R.drawable.grocery_plastic_cutlery,
    "kairos:plastic-wrap" to R.drawable.grocery_plastic_wrap,
    "kairos:pork-chops" to R.drawable.grocery_pork_chops,
    "kairos:protein-powder" to R.drawable.grocery_protein_powder,
    "kairos:ravioli" to R.drawable.grocery_ravioli,
    "kairos:razors" to R.drawable.grocery_razors,
    "kairos:red-onion" to R.drawable.grocery_red_onion,
    "kairos:red-potato" to R.drawable.grocery_red_potato,
    "kairos:ribeye-steak" to R.drawable.grocery_ribeye_steak,
    "kairos:ribs" to R.drawable.grocery_ribs,
    "kairos:rice-bag" to R.drawable.grocery_rice_bag,
    "kairos:romaine-lettuce" to R.drawable.grocery_romaine_lettuce,
    "kairos:rotisserie-chicken" to R.drawable.grocery_rotisserie_chicken,
    "kairos:russet-potato" to R.drawable.grocery_russet_potato,
    "kairos:salad-dressing" to R.drawable.grocery_salad_dressing,
    "kairos:salami" to R.drawable.grocery_salami,
    "kairos:salsa" to R.drawable.grocery_salsa,
    "kairos:shampoo" to R.drawable.grocery_shampoo,
    "kairos:shredded-cheese" to R.drawable.grocery_shredded_cheese,
    "kairos:sirloin-steak" to R.drawable.grocery_sirloin_steak,
    "kairos:soda-bottle" to R.drawable.grocery_soda_bottle,
    "kairos:soup-bowl" to R.drawable.grocery_soup_bowl,
    "kairos:sour-cream" to R.drawable.grocery_sour_cream,
    "kairos:soy-sauce" to R.drawable.grocery_soy_sauce,
    "kairos:spaghetti" to R.drawable.grocery_spaghetti,
    "kairos:sparkling-water" to R.drawable.grocery_sparkling_water,
    "kairos:spice-jar" to R.drawable.grocery_spice_jar,
    "kairos:sponges" to R.drawable.grocery_sponges,
    "kairos:sports-drink" to R.drawable.grocery_sports_drink,
    "kairos:stir-fry" to R.drawable.grocery_stir_fry,
    "kairos:storage-bags" to R.drawable.grocery_storage_bags,
    "kairos:string-cheese" to R.drawable.grocery_string_cheese,
    "kairos:sugar" to R.drawable.grocery_sugar,
    "kairos:sunscreen" to R.drawable.grocery_sunscreen,
    "kairos:supermarket" to R.drawable.grocery_supermarket,
    "kairos:supplements" to R.drawable.grocery_supplements,
    "kairos:syrup" to R.drawable.grocery_syrup,
    "kairos:tacos-plate" to R.drawable.grocery_tacos_plate,
    "kairos:tissues" to R.drawable.grocery_tissues,
    "kairos:tofu" to R.drawable.grocery_tofu,
    "kairos:tomato-paste" to R.drawable.grocery_tomato_paste,
    "kairos:toothpaste" to R.drawable.grocery_toothpaste,
    "kairos:tortilla-chips" to R.drawable.grocery_tortilla_chips,
    "kairos:tortillas" to R.drawable.grocery_tortillas,
    "kairos:trash-bags" to R.drawable.grocery_trash_bags,
    "kairos:vegetable-oil" to R.drawable.grocery_vegetable_oil,
    "kairos:vinegar" to R.drawable.grocery_vinegar,
    "kairos:vitamins" to R.drawable.grocery_vitamins,
    "kairos:warehouse-club" to R.drawable.grocery_warehouse_club,
    "kairos:yellow-onion" to R.drawable.grocery_yellow_onion,
    "kairos:yogurt" to R.drawable.grocery_yogurt,
    "kairos:ziti" to R.drawable.grocery_ziti,
    "kairos:zucchini" to R.drawable.grocery_zucchini,
    "kairos:milk-carton" to R.drawable.grocery_milk_carton,
    "kairos:coffee-bag" to R.drawable.grocery_coffee_bag,
    "kairos:wine" to R.drawable.grocery_wine,
    "kairos:beer" to R.drawable.grocery_beer,
    "kairos:champagne" to R.drawable.grocery_champagne,
    "kairos:whiskey" to R.drawable.grocery_whiskey,
    "kairos:bourbon" to R.drawable.grocery_bourbon,
    "kairos:vodka" to R.drawable.grocery_vodka,
    "kairos:gin" to R.drawable.grocery_gin,
    "kairos:rum" to R.drawable.grocery_rum,
    "kairos:tequila" to R.drawable.grocery_tequila,
    "kairos:cocktail" to R.drawable.grocery_cocktail,
    "kairos:salmon" to R.drawable.grocery_salmon,
    "kairos:tuna" to R.drawable.grocery_tuna,
    "kairos:tilapia" to R.drawable.grocery_tilapia,
    "kairos:cod" to R.drawable.grocery_cod,
    "kairos:catfish" to R.drawable.grocery_catfish,
    "kairos:mahi-mahi" to R.drawable.grocery_mahi_mahi,
    "kairos:shrimp" to R.drawable.grocery_shrimp,
    "kairos:crab-legs" to R.drawable.grocery_crab_legs,
    "kairos:lobster-tail" to R.drawable.grocery_lobster_tail,
    "kairos:scallops" to R.drawable.grocery_scallops,
    "kairos:mussels" to R.drawable.grocery_mussels,
    "kairos:clams" to R.drawable.grocery_clams,
    "kairos:crawfish" to R.drawable.grocery_crawfish,
    "kairos:fish-fillet" to R.drawable.grocery_fish_fillet,
    "kairos:fish-sticks" to R.drawable.grocery_fish_sticks,
    "kairos:canned-tuna" to R.drawable.grocery_canned_tuna,
    "kairos:imitation-crab" to R.drawable.grocery_imitation_crab,
    "kairos:orange-juice" to R.drawable.grocery_orange_juice,
    "kairos:apple-juice" to R.drawable.grocery_apple_juice,
    "kairos:grape-juice" to R.drawable.grocery_grape_juice,
    "kairos:cranberry-juice" to R.drawable.grocery_cranberry_juice,
    "kairos:lemonade" to R.drawable.grocery_lemonade,
    "kairos:sweet-tea" to R.drawable.grocery_sweet_tea,
    "kairos:iced-tea" to R.drawable.grocery_iced_tea,
    "kairos:kombucha" to R.drawable.grocery_kombucha,
    "kairos:hot-chocolate" to R.drawable.grocery_hot_chocolate,
    "kairos:chocolate-milk" to R.drawable.grocery_chocolate_milk,
    "kairos:apple-cider" to R.drawable.grocery_apple_cider,
    "kairos:coconut-water" to R.drawable.grocery_coconut_water,
    "kairos:chocolate-chips" to R.drawable.grocery_chocolate_chips,
    "kairos:coffee-filters" to R.drawable.grocery_coffee_filters,
    "kairos:k-cups" to R.drawable.grocery_k_cups,
    "kairos:plums" to R.drawable.grocery_plums,
    "kairos:pomegranate" to R.drawable.grocery_pomegranate,
    "kairos:cannoli" to R.drawable.grocery_cannoli,
    "kairos:doritos" to R.drawable.grocery_doritos,
    "kairos:sunflower-seeds" to R.drawable.grocery_sunflower_seeds,
    "kairos:almonds" to R.drawable.grocery_almonds,
    "kairos:cashews" to R.drawable.grocery_cashews,
    "kairos:walnuts" to R.drawable.grocery_walnuts,
    "kairos:pistachios" to R.drawable.grocery_pistachios,
    "kairos:beef-jerky" to R.drawable.grocery_beef_jerky,
    "kairos:turkey-jerky" to R.drawable.grocery_turkey_jerky,
    "kairos:protein-bar" to R.drawable.grocery_protein_bar,
    "kairos:granola" to R.drawable.grocery_granola,
    "kairos:corn-meal" to R.drawable.grocery_corn_meal,
    "kairos:heavy-cream" to R.drawable.grocery_heavy_cream,
    "kairos:whipped-cream-can" to R.drawable.grocery_whipped_cream_can,
    "kairos:whipped-cream-tub" to R.drawable.grocery_whipped_cream_tub,
    "kairos:sliced-cheese" to R.drawable.grocery_sliced_cheese,
    "kairos:black-olives" to R.drawable.grocery_black_olives,
    "kairos:distilled-water" to R.drawable.grocery_distilled_water,
    "kairos:motor-oil" to R.drawable.grocery_motor_oil,
    "kairos:avocado-oil" to R.drawable.grocery_avocado_oil,
    "kairos:car-oil-filter" to R.drawable.grocery_car_oil_filter,
    "kairos:car-air-filter" to R.drawable.grocery_car_air_filter,
    "kairos:birthday-candles" to R.drawable.grocery_birthday_candles,
    "kairos:iceberg-lettuce" to R.drawable.grocery_iceberg_lettuce,
    "kairos:backpacking-food" to R.drawable.grocery_backpacking_food,
    "kairos:pecans" to R.drawable.grocery_pecans,
    "kairos:sliced-almonds" to R.drawable.grocery_sliced_almonds,
    "kairos:dates" to R.drawable.grocery_dates,
    "kairos:cacao" to R.drawable.grocery_cacao,
    "kairos:cocoa-powder" to R.drawable.grocery_cocoa_powder,
    "kairos:espresso-powder" to R.drawable.grocery_espresso_powder,
    "kairos:vanilla-extract" to R.drawable.grocery_vanilla_extract,
    "kairos:cardamom" to R.drawable.grocery_cardamom,
    "kairos:ginger" to R.drawable.grocery_ginger,
    "kairos:ginger-candy" to R.drawable.grocery_ginger_candy,
    "kairos:lemon" to R.drawable.grocery_lemon,
    "kairos:lemon-juice" to R.drawable.grocery_lemon_juice,
    "kairos:lime" to R.drawable.grocery_lime,
    "kairos:lime-juice" to R.drawable.grocery_lime_juice,
    "kairos:green-apple" to R.drawable.grocery_green_apple,
    "kairos:yellow-apple" to R.drawable.grocery_yellow_apple,
    "kairos:cosmic-crisp-apple" to R.drawable.grocery_cosmic_crisp_apple,
    "kairos:raspberries" to R.drawable.grocery_raspberries,
    "kairos:blueberries" to R.drawable.grocery_blueberries,
    "kairos:pumpkin" to R.drawable.grocery_pumpkin,
    "kairos:powdered-sugar" to R.drawable.grocery_powdered_sugar,
    "kairos:yeast" to R.drawable.grocery_yeast,
    "kairos:cilantro" to R.drawable.grocery_cilantro,
    "kairos:parsley" to R.drawable.grocery_parsley,
    "kairos:basil" to R.drawable.grocery_basil,
    "kairos:red-lettuce" to R.drawable.grocery_red_lettuce,
    "kairos:spring-mix" to R.drawable.grocery_spring_mix,
    "kairos:cucumber" to R.drawable.grocery_cucumber,
    "kairos:squash" to R.drawable.grocery_squash,
    "kairos:sliced-pickles" to R.drawable.grocery_sliced_pickles,
    "kairos:black-beans" to R.drawable.grocery_black_beans,
    "kairos:coconut-oil" to R.drawable.grocery_coconut_oil,
    "kairos:tri-tip-steak" to R.drawable.grocery_tri_tip_steak,
    "kairos:t-bone-steak" to R.drawable.grocery_t_bone_steak,
    "kairos:turkey-breast" to R.drawable.grocery_turkey_breast,
    "kairos:pork-roast" to R.drawable.grocery_pork_roast,
    "kairos:lamb" to R.drawable.grocery_lamb,
    "kairos:lamb-chops" to R.drawable.grocery_lamb_chops,
    "kairos:whole-turkey" to R.drawable.grocery_whole_turkey,
    "kairos:whole-chicken" to R.drawable.grocery_whole_chicken,
    "kairos:breakfast-sandwich" to R.drawable.grocery_breakfast_sandwich,
    "kairos:ramen" to R.drawable.grocery_ramen,
    "kairos:jelly" to R.drawable.grocery_jelly,
    "kairos:caramel" to R.drawable.grocery_caramel,
    "kairos:muffin" to R.drawable.grocery_muffin,
    "kairos:chocolate-chip-muffin" to R.drawable.grocery_chocolate_chip_muffin,
    "kairos:pumpkin-muffin" to R.drawable.grocery_pumpkin_muffin,
    "kairos:croissant" to R.drawable.grocery_croissant,
    "kairos:bagels" to R.drawable.grocery_bagels,
)


/**
 * Renders a grocery item's stored icon: a small picture for the "ic:*" tokens the
 * server's catalog guesser emits for items with no matching emoji (napkins,
 * paper towels, bottled water, protein), or the emoji/text otherwise. Unknown
 * tokens fall back to a box.
 */
@Composable
fun GroceryGlyph(
    icon: String,
    emojiStyle: TextStyle,
    size: Dp = 22.dp,
) {
    val drawable = GLYPH_DRAWABLES[icon] ?: KAIROS_DRAWABLES[icon]
    if (drawable != null && KairosThemeState.dark) {
        // Dark mode: the picture sits on a soft light tile (the light page's
        // ground colour), same as the web. The pictures were drawn for light
        // pages and many keep a pale edge or grey shadow that only shows on
        // dark; the tile hides it for every icon at once.
        Box(
            Modifier
                .size(size + 4.dp)
                .clip(RoundedCornerShape(percent = 24))
                .background(GLYPH_TILE)
                .padding(2.dp),
        ) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(size + 4.dp),
        )
    } else {
        // Unknown prefixed token (ic:/kairos:/other) -> box, never the raw string.
        val emoji = if (Regex("^[a-z]+:").containsMatchIn(icon)) "\uD83D\uDCE6" else icon
        if (KairosThemeState.dark) {
            // Emoji get the same tile as the pictures, so a dark list never
            // mixes tiled and bare icons. Sized from the tile (dp -> sp through
            // the density, so a large font setting can't overflow it).
            val box = size + 4.dp
            val fontSize = with(LocalDensity.current) { (box * 0.66f).toSp() }
            Box(
                Modifier
                    .size(box)
                    .clip(RoundedCornerShape(percent = 24))
                    .background(GLYPH_TILE),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = emojiStyle.copy(fontSize = fontSize, lineHeight = fontSize), maxLines = 1)
            }
        } else {
            Text(emoji, style = emojiStyle)
        }
    }
}
