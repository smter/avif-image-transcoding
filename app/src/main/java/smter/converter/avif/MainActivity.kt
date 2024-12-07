package smter.converter.avif

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import smter.converter.avif.ui.theme.AVIF图像转码Theme
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var selectedImageUris by mutableStateOf<List<Uri>>(emptyList())
    private var crfValue by mutableStateOf("28") // 默认CRF值

    //使用可记忆的状态来存储输出目录的路径
    private var outputDir by mutableStateOf("")

    // 注册用于选择图片的ActivityResultLauncher
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val clipData = data.clipData
                    if (clipData != null) {
                        //处理多张图片
                        val uris = mutableListOf<Uri>()
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                        selectedImageUris = uris
                    } else //处理单张图片
                        selectedImageUris = listOf(data.data!!)
                }
                //打印
                println("Selected image URI: $selectedImageUris")
            }
        }

    // 注册用于选择输出文件夹的ActivityResultLauncher
    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                outputDir = it.path ?: "" // 更新 outputDir 状态变量
                // 可以在这里将路径持久化存储
                val sharedPrefs = getPreferences(MODE_PRIVATE)
                val editor = sharedPrefs.edit()
                editor.putString("output_dir", outputDir)
                editor.apply()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在 super.onCreate() 之后初始化 outputDir
        outputDir = getExternalFilesDir(null)?.absolutePath ?: ""
        // 读取保存的导出文件夹路径
        val sharedPrefs = getPreferences(MODE_PRIVATE)
        outputDir = sharedPrefs.getString("output_dir", outputDir) ?: outputDir
        setContent {
            AVIF图像转码Theme {
                MainScreen()
            }
        }
    }

    // 处理共享意图触发
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    // 处理共享意图函数
    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            //处理单个图片
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { selectedImageUris = listOf(it) }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            //处理多张图片
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            uris?.let { selectedImageUris = it }
        }
    }


    //主界面
    @Composable
    fun MainScreen() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = outputDir,
                        onValueChange = { /* 只读，不允许用户直接修改 */ },
                        label = { Text("导出文件夹") },
                        readOnly = true,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { } // 点击触发选择文件夹
                    )
                    Button(
                        modifier = Modifier.padding(8.dp),
                        onClick = { selectFolderLauncher.launch(null) }) { Text("选择输出文件夹") }
                }
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Button(
                        modifier = Modifier.padding(8.dp),
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT)
                            intent.type = "image/*"
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            pickImageLauncher.launch(intent)
                        }) {
                        Text("选择图片")
                    }

                    OutlinedTextField(
                        value = crfValue,
                        onValueChange = { crfValue = it },
                        label = { Text("CRF 值") },
                        modifier = Modifier.padding(8.dp)
                    )
                    Button(
                        onClick = { convertImages() },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text("转换")
                    }
                }
            }
        }
    }

    //转码
    private fun convertImages() {
        for (uri in selectedImageUris) {
            val inputPath = getPathFromURI(uri)
            val outputFileName = "${UUID.randomUUID()}.avif"
            val outputPath = File(outputDir, outputFileName).absolutePath

            val command =
                "-i $inputPath -c:v libaom-av1 -crf $crfValue -b:v 0 $outputPath"
            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                // 转换成功，分享结果
                shareOutput(outputPath)
            } else {
                // 转换失败，处理错误
                val message = "转换失败: ${session.failStackTrace}"
                println(message)
            }
        }
    }

    //分享输出
    private fun shareOutput(outputPath: String) {
        val outputUri = FileProvider.getUriForFile(
            this,
            "smter.converter.avif.fileprovider",
            File(outputPath)
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/avif"
        shareIntent.putExtra(Intent.EXTRA_STREAM, outputUri)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, "分享图像"))
    }

    // 从URI获取文件路径
    private fun getPathFromURI(uri: Uri): String {
        return uri.path ?: ""
    }


}