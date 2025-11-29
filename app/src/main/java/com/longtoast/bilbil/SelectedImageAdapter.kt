package com.longtoast.bilbil

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.databinding.ItemSelectedImageBinding

class SelectedImageAdapter(
    private val imageUris: MutableList<Uri>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<SelectedImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(private val binding: ItemSelectedImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            Glide.with(binding.root.context).load(uri).into(binding.imagePreview)
            binding.buttonDeleteImage.setOnClickListener { onDeleteClick(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemSelectedImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageUris[position], position)
    }

    override fun getItemCount(): Int = imageUris.size
}