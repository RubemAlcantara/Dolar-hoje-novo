package com.cambiopro.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Teste instrumentado (exige emulador/dispositivo). Verifica que a barra inferior navega entre as telas. */
class NavigationSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun bottomBarNavigates() {
        rule.onNodeWithText("Conversor").performClick()
        rule.onNodeWithText("Favoritos").performClick()
        rule.onNodeWithText("Ajustes").performClick()
        rule.onNodeWithText("Início").performClick()
    }
}
