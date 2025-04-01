
package `in`.demesne.classic.credentialmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import `in`.demesne.classic.credentialmanager.HomeFragment.HomeFragmentCallback
import `in`.demesne.classic.credentialmanager.MainFragment.MainFragmentCallback
import `in`.demesne.classic.credentialmanager.R.id
import `in`.demesne.classic.credentialmanager.SignInFragment.SignInFragmentCallback
import `in`.demesne.classic.credentialmanager.SignUpFragment.SignUpFragmentCallback
import `in`.demesne.classic.credentialmanager.databinding.ActivityMainBinding
import android.content.Intent
import android.net.Uri

class MainActivity : AppCompatActivity(), MainFragmentCallback, HomeFragmentCallback,
    SignInFragmentCallback, SignUpFragmentCallback {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DataProvider.initSharedPref(applicationContext)

        if (DataProvider.isSignedIn()) {
            showHome()
        } else {
            loadMainFragment()
        }
        // ATTENTION: This was auto-generated to handle app links.
        val appLinkIntent: Intent = intent
        val appLinkAction: String? = appLinkIntent.action
        val appLinkData: Uri? = appLinkIntent.data
    }

    override fun signup() {
        loadFragment(SignUpFragment(), false)
    }

    override fun signIn() {
        loadFragment(SignInFragment(), false)
    }

    override fun logout() {
        supportFragmentManager.popBackStack("home", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        loadMainFragment()
    }

    private fun loadMainFragment() {
        supportFragmentManager.popBackStack()
        loadFragment(MainFragment(), false)
    }

    override fun showHome() {
        supportFragmentManager.popBackStack()
        loadFragment(HomeFragment(), true, "home")
    }

    private fun loadFragment(fragment: Fragment, flag: Boolean, backstackString: String? = null) {
        DataProvider.configureSignedInPref(flag)
        supportFragmentManager.beginTransaction().replace(id.fragment_container, fragment)
            .addToBackStack(backstackString).commit()
    }

    override fun onBackPressed() {
        if (DataProvider.isSignedIn() || supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}
