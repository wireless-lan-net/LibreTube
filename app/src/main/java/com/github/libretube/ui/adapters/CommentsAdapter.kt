package com.github.libretube.ui.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.Comment
import com.github.libretube.api.obj.CommentsPage
import com.github.libretube.databinding.CommentsRowBinding
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.formatShort
import com.github.libretube.ui.viewholders.CommentsViewHolder
import com.github.libretube.util.ClipboardHelper
import com.github.libretube.util.ImageHelper
import com.github.libretube.util.NavigationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommentsAdapter(
    private val videoId: String,
    private val comments: MutableList<Comment>,
    private val isRepliesAdapter: Boolean = false
) : RecyclerView.Adapter<CommentsViewHolder>() {

    private var isLoading = false
    private lateinit var repliesPage: CommentsPage

    fun clear() {
        val size: Int = comments.size
        comments.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun updateItems(newItems: List<Comment>) {
        val commentsSize = comments.size
        comments.addAll(newItems)
        notifyItemRangeInserted(commentsSize, newItems.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = CommentsRowBinding.inflate(layoutInflater, parent, false)
        return CommentsViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: CommentsViewHolder, position: Int) {
        val comment = comments[position]
        holder.binding.apply {
            if (isRepliesAdapter) {
                root.scaleX = 0.9f
                root.scaleY = 0.9f
            }

            commentInfos.text = comment.author.toString() + " • " + comment.commentedTime.toString()
            commentText.text = comment.commentText.toString()

            ImageHelper.loadImage(comment.thumbnail, commentorImage)
            likesTextView.text = comment.likeCount.formatShort()

            if (comment.verified == true) verifiedImageView.visibility = View.VISIBLE
            if (comment.pinned == true) pinnedImageView.visibility = View.VISIBLE
            if (comment.hearted == true) heartedImageView.visibility = View.VISIBLE
            if (comment.repliesPage != null) repliesAvailable.visibility = View.VISIBLE
            if ((comment.replyCount ?: -1L) > 0L) {
                repliesCount.text = comment.replyCount?.formatShort()
            }

            commentorImage.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, comment.commentorUrl)
            }

            repliesRecView.layoutManager = LinearLayoutManager(root.context)
            lateinit var repliesAdapter: CommentsAdapter
            repliesAdapter = CommentsAdapter(
                videoId,
                mutableListOf(),
                true
            )
            repliesRecView.adapter = repliesAdapter
            if (!isRepliesAdapter && comment.repliesPage != null) {
                root.setOnClickListener {
                    showMoreReplies(comment.repliesPage, showMore, repliesAdapter)
                }
            }

            root.setOnLongClickListener {
                ClipboardHelper(root.context).save(comment.commentText.toString())
                Toast.makeText(root.context, R.string.copied, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun showMoreReplies(nextPage: String, showMoreBtn: Button, repliesAdapter: CommentsAdapter) {
        when {
            repliesAdapter.itemCount.equals(0) -> {
                fetchReplies(nextPage) {
                    repliesAdapter.updateItems(it.comments)
                    if (repliesPage.nextpage == null) {
                        showMoreBtn.visibility = View.GONE
                        return@fetchReplies
                    }
                    showMoreBtn.visibility = View.VISIBLE
                    showMoreBtn.setOnClickListener {
                        if (repliesPage.nextpage == null) {
                            it.visibility = View.GONE
                            return@setOnClickListener
                        }
                        fetchReplies(
                            repliesPage.nextpage!!
                        ) {
                            repliesAdapter.updateItems(repliesPage.comments)
                        }
                    }
                }
            }
            else -> {
                repliesAdapter.clear()
                showMoreBtn.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return comments.size
    }

    private fun fetchReplies(nextPage: String, onFinished: (CommentsPage) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isLoading) return@launch
            isLoading = true
            repliesPage = try {
                RetrofitInstance.api.getCommentsNextPage(videoId, nextPage)
            } catch (e: Exception) {
                Log.e(TAG(), "IOException, you might not have internet connection")
                return@launch
            }
            withContext(Dispatchers.Main) {
                onFinished.invoke(repliesPage)
            }
            isLoading = false
        }
    }
}
