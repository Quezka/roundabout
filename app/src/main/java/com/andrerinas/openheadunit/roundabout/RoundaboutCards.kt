package com.andrerinas.openheadunit.roundabout

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.andrerinas.openheadunit.R
import java.util.Calendar

// roundabout drive card (greeting + date + gauge) + media card @ home main page
// no cards in layout (portrait) -> every call = no-op
class RoundaboutCards(private val root: View) {
    private val ctx = root.context
    private val drive: View? = root.findViewById(R.id.rb_drive_card)
    private val media: View? = root.findViewById(R.id.rb_media_card)
    private val gauge: SpeedGaugeView? = root.findViewById(R.id.rb_gauge)
    private val watcher = MediaWatcher(ctx) { renderMedia(it) }
    private var state = MediaState.NONE

    val present: Boolean get() = drive != null || media != null

    init {
        gauge?.unit = ctx.getString(R.string.rb_speed_unit)
        gauge?.mode = loadWidget()
        drive?.findViewById<View>(R.id.rb_drive_widget)?.setOnClickListener { pickWidget(it) }
        media?.let { m ->
            m.findViewById<View>(R.id.rb_media_prev)?.setOnClickListener { watcher.send(MediaAction.PREVIOUS) }
            m.findViewById<View>(R.id.rb_media_toggle)?.setOnClickListener { watcher.send(MediaAction.TOGGLE) }
            m.findViewById<View>(R.id.rb_media_next)?.setOnClickListener { watcher.send(MediaAction.NEXT) }
            m.findViewById<View>(R.id.rb_media_grant)?.setOnClickListener { openListenerSettings() }
            m.findViewById<View>(R.id.rb_media_player)?.setOnClickListener { openPlayer() }
        }
    }

    fun start() {
        if (media == null) return
        watcher.start()
        renderMedia(lastIncoming)
    }

    fun stop() = watcher.stop()

    fun renderInfo(date: String, speedKmh: Int?) {
        drive ?: return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> R.string.rb_greeting_morning
            in 12..17 -> R.string.rb_greeting_afternoon
            in 18..22 -> R.string.rb_greeting_evening
            else -> R.string.rb_greeting_night
        }
        drive.findViewById<TextView>(R.id.rb_greeting)?.setText(greeting)
        drive.findViewById<TextView>(R.id.rb_date)?.text = date
        gauge?.setSpeed(speedKmh)
    }

    private var lastIncoming = MediaState.NONE

    private fun renderMedia(incoming: MediaState) {
        lastIncoming = incoming
        // own session = android auto audio ; without a live session it only holds a placeholder
        val s = if (incoming.packageName == ctx.packageName && !aaLive()) MediaState.NONE else incoming
        state = s
        val m = media ?: return
        val access = watcher.hasAccess()
        val title = m.findViewById<TextView>(R.id.rb_media_title)
        val artist = m.findViewById<TextView>(R.id.rb_media_artist)
        val grant = m.findViewById<View>(R.id.rb_media_grant)
        grant?.visibility = if (access || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) View.GONE else View.VISIBLE
        when {
            !access && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                title?.setText(R.string.rb_media_access_needed)
                artist?.visibility = View.GONE
            }
            s.hasMedia -> {
                title?.text = s.title
                artist?.visibility = View.VISIBLE
                artist?.text = s.artist ?: ctx.getString(R.string.rb_unknown_artist)
            }
            else -> {
                title?.setText(R.string.rb_nothing_playing)
                artist?.visibility = View.GONE
            }
        }
        m.findViewById<ImageView>(R.id.rb_media_toggle_icon)
            ?.setImageResource(if (s.playing) R.drawable.rb_ic_pause else R.drawable.rb_ic_play)
        m.findViewById<View>(R.id.rb_media_toggle)?.contentDescription =
            ctx.getString(if (s.playing) R.string.rb_media_pause else R.string.rb_media_play)

        val icon = m.findViewById<ImageView>(R.id.rb_media_app_icon)
        val label = m.findViewById<TextView>(R.id.rb_media_app)
        val pkg = s.packageName
        val info = pkg?.let { try { ctx.packageManager.getApplicationInfo(it, 0) } catch (e: Exception) { null } }
        if (pkg == ctx.packageName) {
            // own session = android auto audio from the phone
            icon?.setImageResource(R.drawable.rb_ic_aa)
            icon?.let { ImageViewCompat.setImageTintList(it, null) }
            label?.setText(R.string.rb_android_auto)
        } else if (info != null) {
            icon?.setImageDrawable(ctx.packageManager.getApplicationIcon(info))
            icon?.let { ImageViewCompat.setImageTintList(it, null) }
            label?.text = ctx.packageManager.getApplicationLabel(info)
        } else {
            icon?.setImageResource(R.drawable.rb_ic_note)
            icon?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.rb_accent))) }
            label?.text = ""
        }
    }

    private fun aaLive(): Boolean = try {
        val st = com.andrerinas.openheadunit.App.provide(ctx).commManager.connectionState.value
        st is com.andrerinas.openheadunit.connection.CommManager.ConnectionState.HandshakeComplete ||
            st is com.andrerinas.openheadunit.connection.CommManager.ConnectionState.TransportStarted
    } catch (e: Exception) { false }

    // re-read when the session comes or goes
    fun refreshMedia() = renderMedia(lastIncoming)

    // oh connection card -> roundabout look: card surface, aa mark, orange pill with the mark
    // run after every oh paint of the card <- oh repaints it per state
    fun styleConnectionCard() {
        val card = root.findViewById<View>(R.id.home_primary_card) ?: return
        if (drive == null) return
        card.background = ContextCompat.getDrawable(ctx, R.drawable.rb_card_bg)
        val density = ctx.resources.displayMetrics.density
        root.findViewById<View>(R.id.home_primary_icon_tile)?.apply {
            visibility = View.VISIBLE
            background = null
            val px = (64 * density).toInt()
            layoutParams?.let { if (it.width != px) { it.width = px; it.height = px; layoutParams = it } }
        }
        root.findViewById<View>(R.id.home_primary_icon)?.apply {
            val px = (60 * density).toInt()
            layoutParams?.let { if (it.width != px) { it.width = px; it.height = px; layoutParams = it } }
        }
        root.findViewById<ImageView>(R.id.home_primary_icon)?.apply {
            clearColorFilter()
            setImageResource(R.drawable.rb_ic_aa)
        }
        val ink = ContextCompat.getColor(ctx, R.color.rb_text)
        val inkMuted = ContextCompat.getColor(ctx, R.color.rb_muted)
        root.findViewById<TextView>(R.id.home_primary_kicker)?.setTextColor(ContextCompat.getColor(ctx, R.color.rb_accent))
        root.findViewById<TextView>(R.id.home_primary_title)?.setTextColor(ink)
        root.findViewById<TextView>(R.id.home_primary_subtitle)?.setTextColor(inkMuted)
        root.findViewById<TextView>(R.id.home_primary_detail)?.setTextColor(inkMuted)
        root.findViewById<TextView>(R.id.home_primary_action)?.apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.rb_btn_primary)
            setTextColor(ContextCompat.getColor(ctx, R.color.rb_on_accent))
            contentDescription?.let { if (text.toString() != it.toString()) text = it }
            // outline mark on the orange pill -> dark ink, same as the label
            val mark = ContextCompat.getDrawable(ctx, R.drawable.rb_ic_aa)?.mutate()?.let {
                androidx.core.graphics.drawable.DrawableCompat.wrap(it).also { d ->
                    androidx.core.graphics.drawable.DrawableCompat.setTint(d, ContextCompat.getColor(ctx, R.color.rb_on_accent))
                }
            }
            val px = (32 * resources.displayMetrics.density).toInt()
            mark?.setBounds(0, 0, px, px)
            setCompoundDrawables(mark, null, null, null)
            compoundDrawablePadding = (12 * resources.displayMetrics.density).toInt()
            typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.manrope_bold)
        }
        root.findViewById<TextView>(R.id.home_primary_cancel)?.apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.rb_btn_tonal)
            setTextColor(ink)
        }
    }

    // drive card dial: speedometer | analog clock | digital clock ; kept in settings prefs
    private fun prefs() = ctx.getSharedPreferences(com.andrerinas.openheadunit.utils.Settings.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private fun loadWidget(): SpeedGaugeView.Mode =
        try { SpeedGaugeView.Mode.valueOf(prefs().getString(WIDGET_KEY, null) ?: "SPEED") } catch (e: Exception) { SpeedGaugeView.Mode.SPEED }

    private fun pickWidget(anchor: View) {
        val choices = listOf(
            SpeedGaugeView.Mode.SPEED to R.string.rb_widget_speed,
            SpeedGaugeView.Mode.ANALOG_CLOCK to R.string.rb_widget_analog,
            SpeedGaugeView.Mode.DIGITAL_CLOCK to R.string.rb_widget_digital,
        )
        android.widget.PopupMenu(ctx, anchor).apply {
            choices.forEachIndexed { i, (mode, label) ->
                menu.add(0, i, i, label).apply {
                    isCheckable = true
                    isChecked = gauge?.mode == mode
                }
            }
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener { item ->
                val mode = choices[item.itemId].first
                gauge?.mode = mode
                prefs().edit().putString(WIDGET_KEY, mode.name).apply()
                true
            }
            show()
        }
    }

    private fun openPlayer() {
        val pkg = state.packageName ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    private fun openListenerSettings() {
        try {
            ctx.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        private const val WIDGET_KEY = "rb-drive-widget"
    }
}
