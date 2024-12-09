package smter.converter.avif

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import smter.converter.avif.ui.theme.AVIF图像转码Theme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class MainActivity : ComponentActivity() {

    private var selectedImageUris by mutableStateOf<List<Uri>>(emptyList())
    private var crfValue by mutableStateOf("75") // 默认CRF值

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
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                outputDir = uri.toString() ?: "" // 更新 outputDir 状态变量
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
        // 启用 FFmpeg 日志回调
        FFmpegKitConfig.enableLogCallback { log ->
            if (log.message.contains("Permission denied")) {
                Toast.makeText(this, log.message, Toast.LENGTH_SHORT).show()
            }
        }
        setContent {
            AVIF图像转码Theme {
                MyApp(modifier = Modifier)
            }
        }
        //检查权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //请求权限
            val PERMISSIONS_REQUEST_CODE = 1
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
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
    fun MyApp(modifier: Modifier) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    OutlinedTextField(
                        value = outputDir,
                        onValueChange = { /* 只读，不允许用户直接修改 */ },
                        label = { Text("导出文件夹") },
                        readOnly = true,
                        modifier = Modifier
                            .padding(vertical = 16.dp, horizontal = 8.dp)
                    )
                    Button(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        onClick = { selectFolderLauncher.launch(null) }) { Text("选择输出文件夹") }
                }
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
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
                        label = { Text("质量 值") },
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                    )
                    Button(
                        onClick = { convertImages() },
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
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
            val inputPath = FFmpegKitConfig.getSafParameterForRead(this, uri)
            val outputFileName = "${UUID.randomUUID()}.webp"
            val outputPath = "${cacheDir}/${outputFileName}"
            val command =
                "-i $inputPath -q ${crfValue.toInt()} -b:v 0 $outputPath"
            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                // 转换成功
                copyFileToOutputDir(File(outputPath), outputFileName, outputDir.toUri())
                // 分享结果
                Toast.makeText(this, "转换成功", Toast.LENGTH_SHORT).show()
                shareOutput(outputPath.toUri())
            } else {
                // 转换失败，处理错误
                val message = "转换失败: ${session.logs}"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                println(message)
            }
        }
    }

    // 复制文件到 outputDir
    private fun copyFileToOutputDir(sourceFile: File, fileName: String, outputDir: Uri) {
        val contentResolver: ContentResolver = this.contentResolver
        // 获取 DocumentFile 对象，表示选定的目录
        val directory = DocumentFile.fromTreeUri(this, outputDir)
        // 使用 DocumentsContract 创建新文件
        val newFileUri =
            directory?.createFile("image/webp", fileName)
        val outputStream = newFileUri?.let { contentResolver.openOutputStream(it.uri) }
        if (outputStream != null) {
            outputStream.write(sourceFile.readBytes())
            outputStream.close()
        }
    }

    //分享输出
    private fun shareOutput(outputPath: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/avif"
        shareIntent.putExtra(Intent.EXTRA_STREAM, outputPath)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, "分享图像"))
    }

    // 从URI获取文件路径
    private fun getPathFromURI(uri: Uri): String {
        return uri.path ?: ""
    }

    @Composable
    @Preview
    fun preview() {
        MyApp(modifier = Modifier)
    }


}