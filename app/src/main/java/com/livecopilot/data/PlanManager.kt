package com.livecopilot.data

import android.content.Context
import android.content.SharedPreferences

enum class Plan { FREE, PRO }

class PlanManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPlan(): Plan {
        val value = prefs.getString(KEY_PLAN, Plan.FREE.name) ?: Plan.FREE.name
        return runCatching { Plan.valueOf(value) }.getOrElse { Plan.FREE }
    }

    fun isPro(): Boolean = getPlan() == Plan.PRO
    fun isFree(): Boolean = getPlan() == Plan.FREE

    fun setPlan(plan: Plan) {
        prefs.edit().putString(KEY_PLAN, plan.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "livecopilot_plan"
        private const val KEY_PLAN = "plan"
        const val MAX_FREE_ITEMS = 24
    }
}
