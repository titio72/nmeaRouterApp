package com.aboni.n2kRouter

import com.aboni.marine.ui.theme.ResolvedTheme

/** The marine-kit XML theme used by the view-based screens for [theme]. */
fun xmlThemeRes(theme: ResolvedTheme): Int = when (theme) {
    ResolvedTheme.Light -> com.aboni.marine.ui.R.style.Theme_Marine_Light
    ResolvedTheme.Dark -> com.aboni.marine.ui.R.style.Theme_Marine_Dark
    ResolvedTheme.Night -> com.aboni.marine.ui.R.style.Theme_Marine_Night
}
