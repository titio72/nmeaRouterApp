package com.aboni.n2kRouter

import com.aboni.marine.ui.R
import com.aboni.marine.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun eachResolvedThemeUsesItsMarineXmlTheme() {
        assertEquals(R.style.Theme_Marine_Light, xmlThemeRes(ResolvedTheme.Light))
        assertEquals(R.style.Theme_Marine_Dark, xmlThemeRes(ResolvedTheme.Dark))
        assertEquals(R.style.Theme_Marine_Night, xmlThemeRes(ResolvedTheme.Night))
    }
}
