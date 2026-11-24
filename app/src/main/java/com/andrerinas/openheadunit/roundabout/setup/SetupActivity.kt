package com.andrerinas.openheadunit.roundabout.setup

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.app.BaseActivity
import com.andrerinas.openheadunit.main.MainActivity
import com.andrerinas.openheadunit.main.OnboardingActivity
import com.andrerinas.openheadunit.roundabout.system.BluetoothPanel
import com.andrerinas.openheadunit.roundabout.system.LanguagePanel
import com.andrerinas.openheadunit.roundabout.system.Panel
import com.andrerinas.openheadunit.roundabout.system.RbUi
import com.andrerinas.openheadunit.roundabout.system.TabletProfile
import com.andrerinas.openheadunit.roundabout.system.TimePanel
import com.andrerinas.openheadunit.roundabout.system.WifiPanel
import com.andrerinas.openheadunit.utils.Settings

// roundabout first boot = replaces stock setup wizard + oh onboarding
// grants silent (priv-app) => no permission steps ; tablet profile @ done
class SetupActivity : BaseActivity() {

    enum class Step(val title: Int, val skippable: Boolean) {
        WELCOME(R.string.rb_setup_welcome, false),
        LANGUAGE(R.string.rb_sys_language, false),
        WIFI(R.string.rb_sys_wifi, true),
        TIME(R.string.rb_sys_date_time, false),
        PHONE(R.string.rb_setup_phone, true),
        AUTO(R.string.rb_android_auto, false),
        CAR(R.string.rb_setup_car, false),
        DONE(R.string.rb_setup_done, false),
    }

    private lateinit var ui: RbUi
    private lateinit var settings: Settings
    private lateinit var dots: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var back: View
    private lateinit var skip: View
    private lateinit var next: android.widget.TextView
    private var panel: Panel? = null
    private var step = Step.WELCOME
    private var safetyAccepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 21) { finishSetup(); return }
        ui = RbUi(this)
        settings = Settings(this)
        safetyAccepted = settings.hasAcceptedDisclaimer
        step = savedInstanceState?.getString(KEY)?.let { Step.valueOf(it) } ?: Step.WELCOME
        setContentView(build())
        render()
        // profile early => wifi + bluetooth + time ok during setup
        Thread { TabletProfile.apply(applicationContext) }.start()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString(KEY, step.name)
    }

    override fun onDestroy() {
        panel?.stop()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step.ordinal > 0) go(-1)
    }

    private fun build(): View {
        val root = ui.vertical().apply {
            setBackgroundColor(Color.BLACK)
            setPadding(ui.dp(32f), ui.dp(20f), ui.dp(32f), ui.dp(20f))
        }
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(24f)))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(12f), 0, 0)
        }
        back = ui.button(getString(R.string.back), primary = false) { go(-1) }
        bar.addView(back)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        skip = ui.text(getString(R.string.rb_setup_skip), 20f, strong = true, muted = true).apply {
            setPadding(ui.dp(24f), ui.dp(18f), ui.dp(24f), ui.dp(18f))
            setOnClickListener { go(+1) }
        }
        bar.addView(skip)
        next = ui.button(getString(R.string.rb_setup_next)) { onNext() }
        bar.addView(next)
        root.addView(bar)
        return root
    }

    private fun renderDots() {
        dots.removeAllViews()
        Step.values().forEach { s ->
            val on = s == step
            dots.addView(View(this).apply {
                background = ui.rounded(ui.color(if (s.ordinal <= step.ordinal) R.color.rb_accent else R.color.rb_container_highest), 6f)
                alpha = if (on) 1f else 0.7f
            }, LinearLayout.LayoutParams(ui.dp(if (on) 36f else 12f), ui.dp(12f)).apply { setMargins(ui.dp(5f), 0, ui.dp(5f), 0) })
        }
    }

    private fun go(delta: Int) {
        val target = (step.ordinal + delta).coerceIn(0, Step.values().lastIndex)
        if (target == step.ordinal) return
        step = Step.values()[target]
        render()
    }

    private fun onNext() {
        when (step) {
            Step.DONE -> finishSetup()
            Step.CAR -> if (!safetyAccepted) safety() else go(+1)
            else -> go(+1)
        }
    }

    private fun render() {
        panel?.stop()
        panel = null
        renderDots()
        back.visibility = if (step == Step.WELCOME) View.INVISIBLE else View.VISIBLE
        skip.visibility = if (step.skippable) View.VISIBLE else View.GONE
        next.text = getString(when (step) {
            Step.WELCOME -> R.string.rb_setup_start
            Step.DONE -> R.string.rb_setup_drive
            else -> R.string.rb_setup_next
        })
        body.removeAllViews()
        val page = when (step) {
            Step.WELCOME -> welcome()
            Step.DONE -> done()
            else -> scrolled()
        }
        body.addView(page)
        page.alpha = 0f
        page.translationX = ui.dp(40f).toFloat()
        page.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun scrolled(): View {
        val col = ui.vertical().apply { setPadding(ui.dp(120f), ui.dp(12f), ui.dp(120f), ui.dp(24f)) }
        col.addView(ui.title(getString(step.title)))
        val content = ui.vertical()
        col.addView(content)
        when (step) {
            Step.LANGUAGE -> attach(LanguagePanel(ui) { recreate() }, content)
            Step.WIFI -> {
                col.addView(ui.note(getString(R.string.rb_setup_wifi_note)), 1)
                attach(WifiPanel(ui), content)
            }
            Step.TIME -> attach(TimePanel(ui), content)
            Step.PHONE -> {
                col.addView(ui.note(getString(R.string.rb_setup_phone_note)), 1)
                attach(BluetoothPanel(ui), content)
            }
            Step.AUTO -> auto(content)
            Step.CAR -> car(content)
            else -> {}
        }
        return ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) }
    }

    private fun attach(p: Panel, into: LinearLayout) {
        panel = p
        p.attach(into)
    }

    private fun welcome(): View {
        val col = ui.vertical().apply { gravity = Gravity.CENTER }
        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.rb_mini_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(ui.dp(420f), ui.dp(180f)))
        col.addView(ui.text(getString(R.string.title), 48f, strong = true).apply { setPadding(0, ui.dp(16f), 0, ui.dp(4f)) })
        col.addView(ui.text(getString(R.string.rb_setup_welcome_sub), 22f, muted = true).apply { gravity = Gravity.CENTER })
        return col
    }

    private fun auto(content: LinearLayout) {
        val kinds = listOf(Settings.ConnectionKind.USB_CABLE, Settings.ConnectionKind.WIFI)
        val sel = kinds.indexOf(settings.primaryConnection).coerceAtLeast(0)
        content.addView(ui.choice(listOf(getString(R.string.rb_setup_aa_usb), getString(R.string.rb_setup_aa_wireless)), sel) { i ->
            settings.primaryConnection = kinds[i]
            render()
        })
        val steps = if (kinds[sel] == Settings.ConnectionKind.USB_CABLE) R.array.rb_setup_aa_usb_steps else R.array.rb_setup_aa_wireless_steps
        content.addView(ui.header(getString(R.string.rb_setup_on_phone)))
        resources.getStringArray(steps).forEachIndexed { i, line ->
            content.addView(ui.row("${i + 1}.  $line").view)
        }
        content.addView(ui.spacer(8f))
        content.addView(ui.row(getString(R.string.rb_setup_aa_tune), getString(R.string.rb_setup_aa_tune_sub), ui.chevron()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }.view)
    }

    private fun car(content: LinearLayout) {
        content.addView(ui.header(getString(R.string.rb_widget_pick)))
        val modes = listOf("SPEED", "ANALOG_CLOCK", "DIGITAL_CLOCK")
        val prefs = getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val sel = modes.indexOf(prefs.getString(DIAL_KEY, "SPEED")).coerceAtLeast(0)
        content.addView(ui.choice(listOf(getString(R.string.rb_widget_speed), getString(R.string.rb_widget_analog), getString(R.string.rb_widget_digital)), sel) { i ->
            prefs.edit().putString(DIAL_KEY, modes[i]).apply()
        })
        content.addView(ui.header(getString(R.string.rb_setup_drive_side)))
        content.addView(ui.choice(listOf(getString(R.string.rb_setup_lhd), getString(R.string.rb_setup_rhd)), if (settings.rightHandDrive) 1 else 0) { i ->
            settings.rightHandDrive = i == 1
        })
        content.addView(ui.header(getString(R.string.disclaimer_title)))
        content.addView(ui.text(Html.fromHtml(getString(R.string.disclaimer_text)), 17f, muted = true).apply {
            setPadding(ui.dp(4f), 0, ui.dp(4f), ui.dp(8f))
        })
        content.addView(ui.toggle(getString(R.string.onb_safety_accept), null, safetyAccepted) { on -> safetyAccepted = on })
    }

    private fun safety() {
        ui.ask(getString(R.string.disclaimer_title), Html.fromHtml(getString(R.string.disclaimer_text)), getString(R.string.disclaimer_accept)) {
            safetyAccepted = true
            go(+1)
        }
    }

    private fun done(): View {
        val col = ui.vertical().apply { gravity = Gravity.CENTER }
        col.addView(ImageView(this).apply { setImageResource(R.drawable.rb_ic_aa) },
            LinearLayout.LayoutParams(ui.dp(120f), ui.dp(120f)))
        col.addView(ui.text(getString(R.string.rb_setup_done), 44f, strong = true).apply { setPadding(0, ui.dp(20f), 0, ui.dp(6f)) })
        col.addView(ui.text(getString(R.string.rb_setup_done_sub), 22f, muted = true).apply { gravity = Gravity.CENTER })
        return col
    }

    private fun finishSetup() {
        val s = Settings(this)
        s.hasAcceptedDisclaimer = safetyAccepted || s.hasAcceptedDisclaimer
        s.hasCompletedSetupWizard = true
        s.onboardingVersion = OnboardingActivity.CURRENT_ONBOARDING_VERSION
        s.commit()
        getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(DONE_KEY, true).apply()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object {
        private const val KEY = "rb-setup-step"
        const val DONE_KEY = "rb-setup-done"
        private const val DIAL_KEY = "rb-drive-widget"

        fun needed(context: Context): Boolean =
            !context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).getBoolean(DONE_KEY, false)
    }
}
