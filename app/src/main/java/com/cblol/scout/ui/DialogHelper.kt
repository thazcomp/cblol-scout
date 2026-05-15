package com.cblol.scout.ui

import android.content.Context
import com.cblol.scout.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Helper para criar dialogs estilizados consistentes com o tema do app.
 *
 * Usa MaterialAlertDialogBuilder com ThemeOverlay.CBLOLScout.Dialog,
 * que define:
 *  - Fundo navy com borda dourada (drawable/bg_dialog_cblol.xml)
 *  - Título em dourado, bold, letterSpacing
 *  - Body em creme com lineSpacing 1.2
 *  - Botão positivo em dourado bold, negativo/neutral em creme variant
 *
 * Uso:
 *   stylizedDialog(this)
 *       .setTitle("Título")
 *       .setMessage("Mensagem")
 *       .setPositiveButton("OK", null)
 *       .show()
 */
fun stylizedDialog(context: Context): MaterialAlertDialogBuilder =
    MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CBLOLScout_Dialog)
