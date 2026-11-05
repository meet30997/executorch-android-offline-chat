package com.example.executorchdemo

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.executorchdemo.data.ExecuTorchRunner
import com.example.executorchdemo.data.ModelManager
import com.example.executorchdemo.domain.repository.ChatRepositoryImpl
import com.example.executorchdemo.ui.ChatScreen
import com.example.executorchdemo.ui.ChatViewModel
import com.example.executorchdemo.ui.theme.ExecuTorchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory {
            val modelManager = ModelManager(applicationContext)
            val runner = ExecuTorchRunner()
            ChatRepositoryImpl(modelManager, runner)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            ExecuTorchTheme {
                ChatScreen(viewModel = viewModel)
            }
        }
    }
}
