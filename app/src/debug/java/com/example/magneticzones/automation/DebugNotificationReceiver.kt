package com.example.magneticzones.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A debug-build hook for exercising the same alert path as the in-app test action. */
class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return
        RuleMonitorService.sendTestAlert(context, ruleId)
    }

    private companion object {
        const val EXTRA_RULE_ID = "rule_id"
    }
}
