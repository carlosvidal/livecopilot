package com.livecopilot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import com.livecopilot.data.Plan
import com.livecopilot.data.PlanManager

class ConfigurationActivity : AppCompatActivity() {
    private lateinit var planManager: PlanManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Preferencias"

        planManager = PlanManager(this)

        val planSwitch = findViewById<SwitchCompat>(R.id.switch_plan)
        val planLabel = findViewById<TextView>(R.id.text_plan_label)

        fun refreshLabel() {
            val isPro = planManager.isPro()
            planLabel.text = if (isPro) "Plan actual: Pro (ilimitado)" else "Plan actual: Free (24 ítems)"
            planSwitch.isChecked = isPro
        }

        refreshLabel()

        planSwitch.setOnCheckedChangeListener { _, isChecked ->
            planManager.setPlan(if (isChecked) Plan.PRO else Plan.FREE)
            refreshLabel()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}