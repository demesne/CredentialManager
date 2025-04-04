/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package `in`.demesne.classic.credentialmanager

import android.content.Context
import android.os.Bundle
import android.view.View
import `in`.demesne.classic.credentialmanager.databinding.FragmentMainBinding

class MainFragment : BaseFragment<FragmentMainBinding>(FragmentMainBinding::inflate) {
    private lateinit var listener: MainFragmentCallback
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as MainFragmentCallback
        } catch (castException: ClassCastException) {
            /** The activity does not implement the listener.  */
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.signUp.setOnClickListener {
            listener.signup()
        }

        binding.signIn.setOnClickListener {
            listener.signIn()
        }
    }

    interface MainFragmentCallback {
        fun signup()
        fun signIn()
    }
}
