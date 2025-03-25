package `in`.demesne.classic.credentialmanager

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.ContentProviderCompat
import androidx.fragment.app.viewModels
import `in`.demesne.classic.credentialmanager.databinding.FragmentSignInBinding

class SignInFragment : BaseFragment<FragmentSignInBinding>(FragmentSignInBinding::inflate) {
    private val viewModel by viewModels<SignInViewModel>()
    private lateinit var listener: SignInFragmentCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as SignInFragmentCallback
        } catch (_: ClassCastException) {
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.signInWithSavedCredentials.setOnClickListener{
            viewModel.login(requireActivity(), binding.username.text.toString(), binding.password.text.toString())
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            configureViews(View.INVISIBLE, true)
            when (state) {
                SessionTokenState.Idle -> {
                }

                SessionTokenState.Loading -> {
                    configureViews(View.VISIBLE, false)
                }

                is SessionTokenState.Error -> {
                    configureViews(View.INVISIBLE, true)
                    activity?.showErrorAlert(state.message)
                }

                is SessionTokenState.Token -> {
                    configureViews(View.INVISIBLE, true)
                    listener.showHome()
                }
            }
        }
    }


    private fun configureViews(visibility: Int, flag: Boolean) {
        configureProgress(visibility)
        binding.signInWithSavedCredentials.isEnabled = flag
    }

    private fun configureProgress(visibility: Int) {
        binding.circularProgressIndicator.visibility = visibility
    }

    interface SignInFragmentCallback {
        fun showHome()
    }
}

sealed class SessionTokenState {
    object Idle : SessionTokenState()
    object Loading : SessionTokenState()
    data class Error(val message: String) : SessionTokenState()
    object Token : SessionTokenState()
}
