package com.kairos.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairos.app.R

private val Gentium = FontFamily(Font(R.font.gentium_plus))

/** Brief startup screen: the logo with the Koine Greek "kairos" beneath it. */
@Composable
fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize().background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.kairos_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text(
            "\u03BA\u03B1\u03B9\u03C1\u03CC\u03C2", // καιρός
            fontFamily = Gentium,
            fontSize = 52.sp,
            color = Color(0xFF0F5C63),
        )
    }
}
