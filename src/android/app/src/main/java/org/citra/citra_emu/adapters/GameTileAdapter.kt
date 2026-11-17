// Copyright 2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.ItemGameTileBinding
import org.citra.citra_emu.model.Game
import org.citra.citra_emu.utils.GameIconUtils

/**
 * Nebula home screen: the row of recently played game tiles.
 * Tapping a tile selects it; tapping the selected tile again launches it.
 */
class GameTileAdapter(
    private val activity: FragmentActivity,
    private val onSelect: (Game) -> Unit,
    private val onLaunch: (Game) -> Unit
) : RecyclerView.Adapter<GameTileAdapter.TileViewHolder>() {
    private var games: List<Game> = emptyList()
    private var selectedPath: String? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newGames: List<Game>) {
        games = newGames
        notifyDataSetChanged()
    }

    fun setSelected(path: String?) {
        val oldIndex = games.indexOfFirst { it.path == selectedPath }
        selectedPath = path
        val newIndex = games.indexOfFirst { it.path == path }
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex)
        }
        if (newIndex >= 0 && newIndex != oldIndex) {
            notifyItemChanged(newIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemGameTileBinding.inflate(inflater, parent, false)
        return TileViewHolder(binding)
    }

    override fun getItemCount(): Int = games.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(games[position])
    }

    inner class TileViewHolder(private val binding: ItemGameTileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(game: Game) {
            val selected = game.path == selectedPath
            val res = binding.root.resources
            val size = res.getDimensionPixelSize(
                if (selected) R.dimen.nebula_tile_selected else R.dimen.nebula_tile
            )
            binding.tileIcon.layoutParams = binding.tileIcon.layoutParams.apply {
                width = size
                height = size
            }
            binding.tileIcon.alpha = if (selected) 1f else 0.85f
            if (selected) {
                binding.tileFrame.setBackgroundResource(R.drawable.bg_nebula_tile_ring)
            } else {
                binding.tileFrame.background = null
            }

            binding.tileTitle.text = game.title
            val showTitle = selected && res.getBoolean(R.bool.nebula_tile_show_title)
            binding.tileTitle.visibility = if (showTitle) View.VISIBLE else View.GONE
            binding.root.contentDescription = game.title

            GameIconUtils.loadGameIcon(activity, game, binding.tileIcon)
            binding.root.setOnClickListener {
                if (selected) onLaunch(game) else onSelect(game)
            }
        }
    }
}
