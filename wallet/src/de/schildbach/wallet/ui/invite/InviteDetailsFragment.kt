/*
 * Copyright 2021 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.ui.invite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.schildbach.wallet.Constants
import de.schildbach.wallet.data.DashPayProfile
import de.schildbach.wallet.data.Invitation
import de.schildbach.wallet.ui.DashPayUserActivity
import de.schildbach.wallet.ui.dashpay.utils.ProfilePictureDisplay
import de.schildbach.wallet.util.KeyboardUtil
import de.schildbach.wallet.util.Toast
import de.schildbach.wallet.util.WalletUtils
import de.schildbach.wallet_test.BuildConfig
import de.schildbach.wallet_test.R
import kotlinx.android.synthetic.main.activity_payments.toolbar
import kotlinx.android.synthetic.main.fragment_invite_details.*


class InviteDetailsFragment : Fragment(R.layout.fragment_invite_details) {

    companion object {
        private const val ARG_IDENTITY_ID = "identity_id"
        private const val ARG_STARTED_FROM_HISTORY = "started_from_history"

        fun newInstance(identity: String, startedFromHistory: Boolean = false): InviteDetailsFragment {
            val fragment = InviteDetailsFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_IDENTITY_ID, identity)
                putBoolean(ARG_STARTED_FROM_HISTORY, startedFromHistory)
            }
            return fragment
        }
    }

    var tagModified = false

    val viewModel by lazy {
        ViewModelProvider(requireActivity()).get(InvitationFragmentViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        toolbar.title = requireContext().getString(R.string.menu_invite_title)
        val appCompatActivity = requireActivity() as AppCompatActivity
        appCompatActivity.setSupportActionBar(toolbar)

        val actionBar = appCompatActivity.supportActionBar
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        preview_button.setOnClickListener {
            showPreviewDialog()
        }
        copy_invitation_link.setOnClickListener {
            copyInvitationLink()
        }
        send_button.setOnClickListener {
            shareInvitation(true)
        }
        send_button.setOnLongClickListener {
            shareInvitation(false)
            true
        }
        tag_edit.doAfterTextChanged {
            tagModified = true
        }
        profile_button.setOnClickListener {
            startActivity(DashPayUserActivity.createIntent(requireContext(), viewModel.invitedUserProfile.value!!))
        }

        initViewModel()
    }

    private fun initViewModel() {
        val identityId = requireArguments().getString(ARG_IDENTITY_ID)!!
        viewModel.identityIdLiveData.value = identityId

        viewModel.invitationLiveData.observe(viewLifecycleOwner, Observer {
            tag_edit.setText(it.memo)
            date.text = WalletUtils.formatDate(it.sentAt);
            memo.text = it.memo
            if (it.acceptedAt != 0L) {
                showClaimed()
            } else {
                showPending(it)
            }
        })

        viewModel.dashPayProfileData.observe(viewLifecycleOwner, Observer {
            setupInvitationPreviewTemplate(it!!)
        })

    }

    private fun showPending(it: Invitation) {
        send_button.isVisible = it.canSendAgain()
        copy_invitation_link.visibility = send_button.visibility
        claimed_view.isVisible = false
        if (!it.canSendAgain()) {
            memo.setText(R.string.invitation_invalid_invite_title)
            pending_view.isVisible = false
        }
    }

    private fun showClaimed() {
        viewModel.invitedUserProfile.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                icon.setImageResource(R.drawable.ic_claimed_invite)
                claimed_view.isVisible = true
                pending_view.isVisible = false
                preview_button.isVisible = false
                ProfilePictureDisplay.display(avatarIcon, it)
                if (it.displayName.isEmpty()) {
                    display_name.text = it.username
                    username.text = ""
                } else {
                    display_name.text = it.displayName
                    username.text = it.username
                }
            }
        })
    }

    private fun shareInvitation(shareImage: Boolean) {
        // save memo to the database
        viewModel.saveTag(tag_edit.text.toString())

        val shortLink = viewModel.invitation.shortDynamicLink
        ShareCompat.IntentBuilder.from(requireActivity()).apply {
            setSubject(getString(R.string.invitation_share_title))
            setText(shortLink)
            if (shareImage) {
                setType(Constants.Invitation.MIMETYPE_WITH_IMAGE)
                val fileUri: Uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.file_attachment", viewModel.invitationPreviewImageFile!!)
                setStream(fileUri)
            } else {
                setType(Constants.Invitation.MIMETYPE)
            }
            setChooserTitle(R.string.invitation_share_message)
            startChooser()
        }
    }

    private fun setupInvitationPreviewTemplate(profile: DashPayProfile) {
        val profilePictureEnvelope: InvitePreviewEnvelopeView = invitation_bitmap_template.findViewById(R.id.bitmap_template_profile_picture_envelope)
        val messageHtml = getString(R.string.invitation_preview_message, "<b>${profile.nameLabel}</b>")
        val message = HtmlCompat.fromHtml(messageHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val messageView = invitation_bitmap_template.findViewById<TextView>(R.id.bitmap_template_message)
        messageView.text = message
        ProfilePictureDisplay.display(profilePictureEnvelope.avatarView, profile, false, disableTransition = true,
                listener = object : ProfilePictureDisplay.OnResourceReadyListener {
                    override fun onResourceReady(resource: Drawable?) {
                        invitation_bitmap_template.post {
                            viewModel.saveInviteBitmap(invitation_bitmap_template)
                        }
                    }
                })
    }

    private fun showPreviewDialog() {
        val previewDialog = InvitePreviewDialog.newInstance(requireContext(), viewModel.dashPayProfile!!)
        previewDialog.show(childFragmentManager, null)
    }

    private fun copyInvitationLink() {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.invitation_share_title), viewModel.invitation.shortDynamicLink))
        Toast(context).toast(R.string.receive_copied)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.option_close -> {
                requireActivity().run {
                    KeyboardUtil.hideKeyboard(this, tag_edit)
                    finish()
                }
                true
            }
            android.R.id.home -> {
                requireActivity().onBackPressed()
                return true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // save memo to the database
        if (tagModified)
            viewModel.saveTag(tag_edit.text.toString())
    }
}
