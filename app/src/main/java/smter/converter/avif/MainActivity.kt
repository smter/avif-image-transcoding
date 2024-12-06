package smter.converter.avif

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import smter.converter.avif.ui.theme.AVIF图像转码Theme

class MainActivity : ComponentActivity() {
    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var crfValue by mutableStateOf("28") // 默认CRF值
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedImageUri = result.data?.data
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AVIF图像转码Theme {
                MyApp()
//                Scaffold(modifier = Modifier.padding()) { innerPadding ->
//                    Column(modifier = Modifier.padding(innerPadding)) {
//                        // 选择图像按钮
//                        Button(onClick = { /* 选择图像 */
//                            val intent = Intent(Intent.ACTION_GET_CONTENT)
//                            intent.type = "image/*"
//                            pickImageLauncher.launch(intent)
//                        }) {
//                            Text("选择图像")
//                        }
//
//                    }
//                }
            }
        }
    }
}

@Composable
fun MyApp(modifier: Modifier = Modifier) {
    //开局突脸
    var shouldShowOnboarding by remember { mutableStateOf(true) }
    val names: List<String> =
        listOf<String>("谜语说的道理", "哈拿挪fish", "黑狗灭霸别写").plus(List(1000) { "$it" })
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (shouldShowOnboarding) {
            OnboardingScreen(onContinueClicked = { shouldShowOnboarding = false })
        } else {
            LazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                items(items = names) { name ->
                    Greeting(name = name)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val expandedPadding by animateDpAsState(
        if (expanded) 48.dp else 0.dp, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("你说的对")
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (expanded) {
                    Text(
                        text = "你说的对，但是《原神》是由米哈游自主研发的一款全新开放世界冒险游戏。游戏发生在一个被称作「提瓦特」的幻想世界，在这里，被神选中的人将被授予「神之眼」，导引元素之力。你将扮演一位名为「旅行者」的神秘角色，在自由的旅行中邂逅性格各异、能力独特的同伴们，和他们一起击败强敌，找回失散的亲人。\n" +
                                "\n" +
                                "一个不玩原神的人，无非只有两种可能性。一种是没有能力玩原神。因为买不起高配的手机和抽不起卡等各种自身因素，他的人生都是失败的，第二种可能：有能力却不玩原神的人，在有能力而没有玩原神的想法时，那么这个人的思想境界便低到了一个令人发指的程度。一个有能力的人不付出行动来证明自己，只能证明此人行为素质修养之低下。是灰暗的，是不被真正的社会认可的。 原神怎么你了，我现在每天玩原神都能赚150原石，每个月差不多5000原石的收入，也就是现实生活中每个月5000美元的收入水平，换算过来最少也30000人民币，虽然我只有14岁，但是已经超越了中国绝大多数人(包括你)的水平，这便是原神给我的骄傲的资本。这恰好说明了原神这个IP在线下使玩家体现出来的团结和凝聚力，以及非比寻常的脑洞，这种氛围在如今已经变质的漫展上是难能可贵的，这也造就了原神和玩家间互帮互助的局面，原神负责输出优质内容，玩家自发线下宣传和构思创意脑洞整活，如此良好的游戏发展生态可以说让其他厂商艳羡不已。玩游戏不玩原神，就像四大名著不看红楼梦，说明这人文学造诣和自我修养不足，他理解不了这种内在的阳春白雪的高雅艺术.，他只能看到外表的辞藻堆砌，参不透其中深奥的精神内核,只能度过一个相对失败的人生",
                    )
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Filled.ExpandLess else Filled.ExpandMore,
                    contentDescription = if (expanded)
                        stringResource(R.string.show_less) else stringResource(R.string.show_more)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun GreetingPreview() {
    AVIF图像转码Theme {
        MyApp()
    }
}

@Composable
fun OnboardingScreen(onContinueClicked: () -> Unit, modifier: Modifier = Modifier) {


    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("你玩原神吗!")
        Button(
            modifier = Modifier.padding(vertical = 24.dp),
            onClick = onContinueClicked
        ) {
            Text("我玩原神")
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 320)
@Composable
fun OnboardingPreview() {
    AVIF图像转码Theme {
        OnboardingScreen(onContinueClicked = {})
    }
}