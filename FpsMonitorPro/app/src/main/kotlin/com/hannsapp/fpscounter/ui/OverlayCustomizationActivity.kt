package com.hannsapp.fpscounter.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.databinding.ActivityOverlayCustomizationBinding
import com.hannsapp.fpscounter.utils.Constants

class OverlayCustomizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOverlayCustomizationBinding
    private lateinit var preferencesManager: PreferencesManager

    private var selectedPosition = 0
    private var selectedSize = 1
    private var selectedOpacity = 0.8f
    private var selectedColor = Constants.OVERLAY_COLORS[0]

    private val positionViews = mutableListOf<View>()
    private val sizeViews = mutableListOf<View>()
    private val colorViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOverlayCustomizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager

        loadCurrentSettings()
        setupToolbar()
        setupPositionSelector()
        setupSizeSelector()
        setupOpacitySlider()
        setupColorPicker()
        setupDisplayToggles()
        updatePreview()
    }

    private fun loadCurrentSettings() {
        selectedPosition = preferencesManager.overlayPosition
        selectedSize = preferencesManager.overlaySize
        selectedOpacity = preferencesManager.overlayOpacity
        selectedColor = preferencesManager.overlayColor
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupPositionSelector() {
        positionViews.clear()
        positionViews.addAll(listOf(
            binding.posTopLeft,
            binding.posTopRight,
            binding.posBottomLeft,
            binding.posBottomRight,
            binding.posCenterTop,
            binding.posCenterBottom
        ))

        positionViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                selectPosition(index)
            }
        }

        updatePositionSelection()
    }

    private fun selectPosition(position: Int) {
        selectedPosition = position
        preferencesManager.overlayPosition = position
        updatePositionSelection()
        updatePreview()
    }

    private fun updatePositionSelection() {
        positionViews.forEachIndexed { index, view ->
            val isSelected = index == selectedPosition
            view.background = if (isSelected) {
                ContextCompat.getDrawable(this, R.drawable.button_primary_background)
            } else {
                ContextCompat.getDrawable(this, R.drawable.card_background)
            }
        }
    }

    private fun setupSizeSelector() {
        sizeViews.clear()
        sizeViews.addAll(listOf(
            binding.sizeSmall,
            binding.sizeMedium,
            binding.sizeLarge
        ))

        sizeViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                selectSize(index)
            }
        }

        updateSizeSelection()
    }

    private fun selectSize(size: Int) {
        selectedSize = size
        preferencesManager.overlaySize = size
        updateSizeSelection()
        updatePreview()
    }

    private fun updateSizeSelection() {
        sizeViews.forEachIndexed { index, view ->
            val isSelected = index == selectedSize
            view.background = if (isSelected) {
                ContextCompat.getDrawable(this, R.drawable.button_primary_background)
            } else {
                ContextCompat.getDrawable(this, R.drawable.card_background)
            }
        }
    }

    private fun setupOpacitySlider() {
        binding.sliderOpacity.value = selectedOpacity * 100
        binding.tvOpacityValue.text = "${(selectedOpacity * 100).toInt()}%"

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                selectedOpacity = value / 100f
                preferencesManager.overlayOpacity = selectedOpacity
                binding.tvOpacityValue.text = "${value.toInt()}%"
                updatePreview()
            }
        }
    }

    private fun setupColorPicker() {
        colorViews.clear()
        colorViews.addAll(listOf(
            binding.colorBlue,
            binding.colorGreen,
            binding.colorYellow,
            binding.colorRed,
            binding.colorPurple,
            binding.colorCyan,
            binding.colorWhite
        ))

        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                selectColor(index)
            }
        }

        updateColorSelection()
    }

    private fun selectColor(index: Int) {
        if (index >= 0 && index < Constants.OVERLAY_COLORS.size) {
            selectedColor = Constants.OVERLAY_COLORS[index]
            preferencesManager.overlayColor = selectedColor
            updateColorSelection()
            updatePreview()
        }
    }

    private fun updateColorSelection() {
        val selectedIndex = Constants.OVERLAY_COLORS.indexOf(selectedColor)
        colorViews.forEachIndexed { index, view ->
            val isSelected = index == selectedIndex
            view.scaleX = if (isSelected) 1.2f else 1f
            view.scaleY = if (isSelected) 1.2f else 1f
            view.elevation = if (isSelected) 8f else 0f
        }
    }

    private fun setupDisplayToggles() {
        binding.switchShowFps.isChecked = preferencesManager.showFps
        binding.switchShowMemory.isChecked = preferencesManager.showMemory
        binding.switchShowCpu.isChecked = preferencesManager.showCpu
        binding.switchShowBattery.isChecked = preferencesManager.showBattery
        binding.switchShowTemp.isChecked = preferencesManager.showTemp

        binding.switchShowFps.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showFps = isChecked
            updatePreview()
        }

        binding.switchShowMemory.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showMemory = isChecked
            updatePreview()
        }

        binding.switchShowCpu.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showCpu = isChecked
            updatePreview()
        }

        binding.switchShowBattery.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showBattery = isChecked
            updatePreview()
        }

        binding.switchShowTemp.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showTemp = isChecked
            updatePreview()
        }
    }

    private fun updatePreview() {
        binding.overlayPreviewContainer.removeAllViews()

        val overlayView = createOverlayPreview()
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        when (selectedPosition) {
            Constants.OVERLAY_POSITION_TOP_LEFT -> {
                params.gravity = Gravity.TOP or Gravity.START
            }
            Constants.OVERLAY_POSITION_TOP_RIGHT -> {
                params.gravity = Gravity.TOP or Gravity.END
            }
            Constants.OVERLAY_POSITION_BOTTOM_LEFT -> {
                params.gravity = Gravity.BOTTOM or Gravity.START
            }
            Constants.OVERLAY_POSITION_BOTTOM_RIGHT -> {
                params.gravity = Gravity.BOTTOM or Gravity.END
            }
            Constants.OVERLAY_POSITION_CENTER_TOP -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            Constants.OVERLAY_POSITION_CENTER_BOTTOM -> {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
        }

        params.setMargins(16, 16, 16, 16)
        binding.overlayPreviewContainer.addView(overlayView, params)
    }

    private fun createOverlayPreview(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            
            val bgColor = Color.argb(
                (selectedOpacity * 255).toInt(),
                Color.red(Color.parseColor("#0A1929")),
                Color.green(Color.parseColor("#0A1929")),
                Color.blue(Color.parseColor("#0A1929"))
            )
            setBackgroundColor(bgColor)
            background = ContextCompat.getDrawable(this@OverlayCustomizationActivity, R.drawable.overlay_background)
            background?.alpha = (selectedOpacity * 255).toInt()
        }

        val textSize = when (selectedSize) {
            Constants.OVERLAY_SIZE_SMALL -> 12f
            Constants.OVERLAY_SIZE_MEDIUM -> 16f
            Constants.OVERLAY_SIZE_LARGE -> 20f
            else -> 16f
        }

        if (preferencesManager.showFps) {
            container.addView(createOverlayText("60 FPS", textSize, true))
        }
        if (preferencesManager.showMemory) {
            container.addView(createOverlayText("MEM: 4.2 GB", textSize - 2, false))
        }
        if (preferencesManager.showCpu) {
            container.addView(createOverlayText("CPU: 35%", textSize - 2, false))
        }
        if (preferencesManager.showBattery) {
            container.addView(createOverlayText("BAT: 85%", textSize - 2, false))
        }
        if (preferencesManager.showTemp) {
            container.addView(createOverlayText("TEMP: 42°C", textSize - 2, false))
        }

        if (container.childCount == 0) {
            container.addView(createOverlayText("FPS", textSize, true))
        }

        return container
    }

    private fun createOverlayText(text: String, size: Float, isBold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(selectedColor)
            if (isBold) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            gravity = Gravity.CENTER
        }
    }
}
