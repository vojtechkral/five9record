package cs.ok3vo.five9record

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cs.ok3vo.five9record.databinding.ActivityMainBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.recording.RecordingActivity
import cs.ok3vo.five9record.recording.StartRecordingActivity

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.composeView.setContent {
            MaterialTheme { // TODO: use app's theme
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            icon = { Icon(painterResource(R.drawable.voicemail), "Record") },
                            text = { Text(stringResource(R.string.new_recording)) },
                            onClick = { newRecording() }
                        )
                    }
                ) {
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                    ) {
                        Text("TODO: recordings view")
                    }
                }
            }
        }
    }

    private fun newRecording() {
        val intent = if (Radio.isRunning) {
            Intent(this, RecordingActivity::class.java)
        } else {
            Intent(this, StartRecordingActivity::class.java)
        }
        startActivity(intent)
    }
}
