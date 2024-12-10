package smter.converter.avif

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import smter.converter.avif.ui.theme.AVIF图像转码Theme
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var selectedImageUris by mutableStateOf<List<Uri>>(emptyList())
    private var crfValue by mutableStateOf("28") // 默认CRF值

    //使用可记忆的状态来存储输出目录的路径
    private var outputDir by mutableStateOf("")

    //正在转换状态
    private var isLoading by mutableStateOf(false)

    private var ffmpegLog by mutableStateOf("")

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
            ffmpegLog += log.message
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
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyApp(modifier: Modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text("AVIF图像转码")
                    },
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    if (selectedImageUris.isEmpty())
                        Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
                    else if (isLoading)
                        Toast.makeText(this, "正在转换中 请稍候", Toast.LENGTH_SHORT).show()
                    else if (!isLoading) {
                        isLoading = true
                        //异步 开始转换
                        Thread {
                            convertImages()
                        }.start()
//                        convertImages()
                    }
                }) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else
                        Icon(Icons.Outlined.Bolt, contentDescription = "开始转换")
                }
            }
        ) { innerpadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerpadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    OutlinedTextField(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "导出文件夹"
                            )
                        },
                        value = outputDir,
                        onValueChange = { /* 只读，不允许用户直接修改 */ },
                        label = { Text("导出文件夹") },
                        readOnly = true,
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                    Button(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        onClick = { selectFolderLauncher.launch(null) }) { Text("选择导出文件夹") }
                }
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        onClick = {
                            if (isLoading)
                                Toast.makeText(this@MainActivity, "正在转换中 请稍候", Toast.LENGTH_SHORT).show()
                            else {
                                val intent = Intent(Intent.ACTION_GET_CONTENT)
                                intent.type = "image/*"
                                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                pickImageLauncher.launch(intent)
                            }
                        }) {
                        Text("选择图片")
                    }

                    OutlinedTextField(
                        value = crfValue,
                        onValueChange = { if (it.isNotEmpty()) crfValue = it },
                        label = { Text("CRF 值") },
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "质量 值",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = 1 - (crfValue.toFloat() / 65),
                            onValueChange = { crfValue = (65 - 65 * it).toInt().toString() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        label = { Text("转换日志") },
                        value = ffmpegLog,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .fillMaxSize()
                    )
                }
            }
        }
    }

    //转码
    private fun convertImages() {
        for (uri in selectedImageUris) {
            val inputPath = FFmpegKitConfig.getSafParameterForRead(this, uri)
            val outputFileName = "${UUID.randomUUID()}.avif"
            val outputPath = "${cacheDir}/${outputFileName}"
            val command =
                "-i $inputPath -crf ${crfValue.toInt()} -b:v 0 -threads 4 -cpu-used 4 -row-mt 1 -tiles 2x2  $outputPath"
            val session = FFmpegKit.execute(command)
            if (ReturnCode.isSuccess(session.returnCode)) {
                // 转换成功
                copyFileToOutputDir(File(outputPath), outputFileName, outputDir.toUri())
                Toast.makeText(this, "转换成功", Toast.LENGTH_SHORT).show()
            } else {
                // 转换失败，处理错误
                val message = "转换失败: ${session.logs}"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                println(message)
            }
        }
        isLoading = false
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
    fun Preview() {
        MyApp(modifier = Modifier)
    }


}