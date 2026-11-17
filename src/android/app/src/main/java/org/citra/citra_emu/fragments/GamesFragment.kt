// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.citra.citra_emu.BuildConfig
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.adapters.GameAdapter
import org.citra.citra_emu.adapters.GameTileAdapter
import org.citra.citra_emu.databinding.FragmentGamesBinding
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.CitraSettings
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.updatechecker.UpdateChecker
import org.citra.citra_emu.model.Game
import org.citra.citra_emu.utils.BuildUtil
import org.citra.citra_emu.utils.GameIconUtils
import org.citra.citra_emu.viewmodel.CompressProgressDialogViewModel
import org.citra.citra_emu.viewmodel.GamesViewModel
import org.citra.citra_emu.viewmodel.HomeViewModel

class GamesFragment : Fragment() {
    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!

    private val gamesViewModel: GamesViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private lateinit var gameAdapter: GameAdapter
    private lateinit var tileAdapter: GameTileAdapter
    private var selectedGame: Game? = null

    private val openImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        gameAdapter.handleShortcutImageResult(uri)
    }

    private var shouldCompress: Boolean = true
    private var pendingCompressInvocation: String? = null

    companion object {
        private const val CAROUSEL_SIZE = 10

        fun doCompression(
            fragment: Fragment,
            gamesViewModel: GamesViewModel,
            inputPath: String?,
            outputUri: Uri?,
            shouldCompress: Boolean
        ) {
            if (outputUri != null) {
                val outputPath: String =
                    if (!BuildUtil.isGooglePlayBuild) {
                        "!" + NativeLibrary.getNativePath(outputUri)
                    } else {
                        outputUri.toString()
                    }
                CompressProgressDialogViewModel.reset()
                val dialog = CompressProgressDialogFragment.newInstance(shouldCompress, outputPath)
                dialog.showNow(
                    fragment.requireActivity().supportFragmentManager,
                    CompressProgressDialogFragment.TAG
                )

                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    val status = if (shouldCompress) {
                        NativeLibrary.compressFile(inputPath, outputPath)
                    } else {
                        NativeLibrary.decompressFile(inputPath, outputPath)
                    }

                    fragment.requireActivity().runOnUiThread {
                        dialog.dismiss()
                        @Suppress("ktlint:standard:max-line-length")
                        val resId = when (status) {
                            NativeLibrary.CompressStatus.SUCCESS -> if (shouldCompress) R.string.compress_success else R.string.decompress_success
                            NativeLibrary.CompressStatus.COMPRESS_UNSUPPORTED -> R.string.compress_unsupported
                            NativeLibrary.CompressStatus.COMPRESS_ALREADY_COMPRESSED -> R.string.compress_already
                            NativeLibrary.CompressStatus.COMPRESS_FAILED -> R.string.compress_failed
                            NativeLibrary.CompressStatus.DECOMPRESS_UNSUPPORTED -> R.string.decompress_unsupported
                            NativeLibrary.CompressStatus.DECOMPRESS_NOT_COMPRESSED -> R.string.decompress_not_compressed
                            NativeLibrary.CompressStatus.DECOMPRESS_FAILED -> R.string.decompress_failed
                            NativeLibrary.CompressStatus.INSTALLED_APPLICATION -> R.string.compress_decompress_installed_app
                        }

                        MaterialAlertDialogBuilder(fragment.requireContext())
                            .setMessage(fragment.getString(resId))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()

                        gamesViewModel.reloadGames(false)
                    }
                }
            }
        }
    }

    private val onCompressDecompressLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        doCompression(this, gamesViewModel, pendingCompressInvocation, uri, shouldCompress)
        pendingCompressInvocation = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater)
        return binding.root
    }

    // This is using the correct scope, lint is just acting up
    @SuppressLint("UnsafeRepeatOnLifecycleDetector")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        homeViewModel.setNavigationVisibility(visible = true, animated = true)
        homeViewModel.setStatusBarShadeVisibility(visible = true)

        val inflater = LayoutInflater.from(requireContext())

        gameAdapter = GameAdapter(
            requireActivity() as AppCompatActivity,
            inflater,
            openImageLauncher,
            onRequestCompressOrDecompress = { inputPath, suggestedName, shouldCompress ->
                pendingCompressInvocation = inputPath
                onCompressDecompressLauncher.launch(suggestedName)
                this.shouldCompress = shouldCompress
            }
        )

        binding.gridGames.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                resources.getInteger(R.integer.game_grid_columns)
            )
            adapter = this@GamesFragment.gameAdapter
        }

        // Nebula home screen: recently played tiles and the selected game
        tileAdapter = GameTileAdapter(
            requireActivity(),
            onSelect = { selectGame(it) },
            onLaunch = { gameAdapter.launchGame(binding.root, it) }
        )
        binding.carouselGames.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = tileAdapter
            itemAnimator = null
        }
        gameAdapter.applyArtBlur(binding.heroArt)
        binding.heroPlay.setOnClickListener {
            selectedGame?.let { gameAdapter.launchGame(binding.root, it) }
        }
        binding.heroMore.setOnClickListener { view ->
            selectedGame?.let { gameAdapter.showGamePage(view, it) }
        }
        binding.homeSearchButton.setOnClickListener {
            requireActivity().findViewById<NavigationBarView>(R.id.navigation_view)
                ?.selectedItemId = R.id.searchFragment
        }

        binding.swipeRefresh.apply {
            // Add swipe down to refresh gesture
            setOnRefreshListener {
                gamesViewModel.reloadGames(false)
            }

            // Set theme color to the refresh animation's background
            setProgressBackgroundColorSchemeColor(
                MaterialColors.getColor(
                    binding.swipeRefresh,
                    com.google.android.material.R.attr.colorPrimary
                )
            )
            setColorSchemeColors(
                MaterialColors.getColor(
                    binding.swipeRefresh,
                    com.google.android.material.R.attr.colorOnPrimary
                )
            )

            // Make sure the loading indicator appears even if the layout is told to refresh before being fully drawn
            post {
                if (_binding == null) {
                    return@post
                }
                binding.swipeRefresh.isRefreshing = gamesViewModel.isReloading.value
            }
        }

        viewLifecycleOwner.lifecycleScope.apply {
            launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    gamesViewModel.isReloading.collect { isReloading ->
                        binding.swipeRefresh.isRefreshing = isReloading
                        if (gamesViewModel.games.value.isEmpty() && !isReloading) {
                            binding.noticeText.visibility = View.VISIBLE
                        } else {
                            // Nebula: GONE so the empty-library message takes no space
                            binding.noticeText.visibility = View.GONE
                        }
                    }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    gamesViewModel.games.collectLatest { setAdapter(it) }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    gamesViewModel.shouldSwapData.collect {
                        if (it) {
                            setAdapter(gamesViewModel.games.value)
                            gamesViewModel.setShouldSwapData(false)
                        }
                    }
                }
            }
            launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    gamesViewModel.shouldScrollToTop.collect {
                        if (it) {
                            scrollToTop()
                            gamesViewModel.setShouldScrollToTop(false)
                        }
                    }
                }
            }
        }

        setInsets()
    }

    private fun getMajorVersion(version: String): Int? = version.split('.')[0].toIntOrNull()

    private fun isPrereleaseBuild(): Boolean {
        val version = BuildConfig.GIT_VERSION
        return (
            version.contains("alpha") ||
                version.contains("beta") ||
                version.contains("rc") ||
                version.contains("test")
            )
    }

    override fun onResume() {
        super.onResume()

        // Perform update check
        @Suppress("SimplifyBooleanWithConstants", "RedundantSuppression")
        if (!BuildConfig.DEBUG &&
            !BuildUtil.isGooglePlayBuild &&
            CitraSettings.activeSettings.get(BooleanSetting.CHECK_FOR_UPDATES) &&
            !homeViewModel.updatePromptShown
        ) {
            Thread({
                val checkForPrereleaseUpdates =
                    isPrereleaseBuild() ||
                        (CitraSettings.activeSettings.get(IntSetting.UPDATE_CHECK_CHANNEL) == 1)
                val latestReleaseTag = UpdateChecker.getLatestRelease(checkForPrereleaseUpdates)

                if (!latestReleaseTag.isNullOrEmpty() &&
                    latestReleaseTag != BuildConfig.GIT_VERSION
                ) {
                    val latestMajorVersion = getMajorVersion(latestReleaseTag)
                    val currentMajorVersion = getMajorVersion(BuildConfig.GIT_VERSION)
                    if (latestMajorVersion != null &&
                        currentMajorVersion != null &&
                        currentMajorVersion <= latestMajorVersion
                    ) {
                        UpdateAvailableNotificationFragment.newInstance(
                            latestReleaseTag,
                            checkForPrereleaseUpdates
                        )
                            .show(
                                requireActivity().supportFragmentManager,
                                UpdateAvailableNotificationFragment.TAG
                            )
                    }
                }
            }).start()
            homeViewModel.updatePromptShown = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setAdapter(games: List<Game>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)
        val visibleGames = if (preferences.getBoolean(CitraSettings.PREF_SHOW_HOME_APPS, false)) {
            games
        } else {
            games.filter { !it.isSystemTitle }
        }
        // Nebula: most recently played first
        val sortedGames = visibleGames.sortedByDescending {
            preferences.getLong(it.keyLastPlayedTime, 0L)
        }
        (binding.gridGames.adapter as GameAdapter).submitList(sortedGames)

        binding.gamesCount.text = resources.getQuantityString(
            R.plurals.nebula_titles_count,
            sortedGames.size,
            sortedGames.size
        )
        tileAdapter.submit(sortedGames.take(CAROUSEL_SIZE))
        val hasGames = sortedGames.isNotEmpty()
        binding.carouselGames.visibility = if (hasGames) View.VISIBLE else View.GONE
        binding.heroGroup.visibility = if (hasGames) View.VISIBLE else View.GONE
        binding.allGamesHeader.visibility = if (hasGames) View.VISIBLE else View.GONE
        binding.heroArt.visibility = if (hasGames) View.VISIBLE else View.INVISIBLE

        val stillThere = sortedGames.firstOrNull { it.path == selectedGame?.path }
        val toSelect = stillThere ?: sortedGames.firstOrNull()
        if (toSelect != null) {
            selectGame(toSelect)
        } else {
            selectedGame = null
        }
    }

    private fun selectGame(game: Game) {
        if (_binding == null) {
            return
        }
        selectedGame = game
        tileAdapter.setSelected(game.path)

        val metaParts = listOf(game.company, gameAdapter.translateRegions(game.regions))
            .filter { it.isNotEmpty() }
        binding.heroMeta.text = metaParts.joinToString(" · ")
        binding.heroTitle.text = game.title

        val playTimeSeconds = NativeLibrary.playTimeManagerGetPlayTime(game.titleId)
        val hours = playTimeSeconds / 3600
        val minutes = (playTimeSeconds % 3600) / 60
        val playTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        binding.heroPlaytime.text = getString(R.string.nebula_played, playTime)

        val preferences = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)
        val lastPlayed = preferences.getLong(game.keyLastPlayedTime, 0L)
        binding.heroLastPlayed.text = if (lastPlayed == 0L) {
            getString(R.string.nebula_not_played)
        } else {
            getString(
                R.string.nebula_last_played,
                DateUtils.getRelativeTimeSpanString(
                    lastPlayed,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            )
        }

        GameIconUtils.loadGameIcon(requireActivity(), game, binding.heroArt)
    }

    private fun scrollToTop() {
        if (_binding != null) {
            binding.homeScroll.smoothScrollTo(0, 0)
        }
    }

    private fun setInsets() = ViewCompat.setOnApplyWindowInsetsListener(
        binding.root
    ) { view: View, windowInsets: WindowInsetsCompat ->
        val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val extraListSpacing = resources.getDimensionPixelSize(R.dimen.spacing_large)
        val spacingNavigation = resources.getDimensionPixelSize(R.dimen.spacing_navigation)
        val spacingNavigationRail =
            resources.getDimensionPixelSize(R.dimen.spacing_navigation_rail)

        // Nebula: the page content starts under the status bar; the hero art goes behind it
        binding.homeContent.updatePadding(
            top = barInsets.top + resources.getDimensionPixelSize(R.dimen.spacing_medlarge)
        )
        binding.gridGames.updatePadding(
            bottom = barInsets.bottom + spacingNavigation + extraListSpacing
        )

        binding.swipeRefresh.setProgressViewEndTarget(
            false,
            barInsets.top + resources.getDimensionPixelSize(R.dimen.spacing_refresh_end)
        )

        val leftInsets = barInsets.left + cutoutInsets.left
        val rightInsets = barInsets.right + cutoutInsets.right
        val mlpSwipe = binding.swipeRefresh.layoutParams as MarginLayoutParams
        if (view.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            mlpSwipe.leftMargin = leftInsets + spacingNavigationRail
            mlpSwipe.rightMargin = rightInsets
        } else {
            mlpSwipe.leftMargin = leftInsets
            mlpSwipe.rightMargin = rightInsets + spacingNavigationRail
        }
        binding.swipeRefresh.layoutParams = mlpSwipe

        binding.noticeText.updatePadding(bottom = spacingNavigation)

        windowInsets
    }
}
