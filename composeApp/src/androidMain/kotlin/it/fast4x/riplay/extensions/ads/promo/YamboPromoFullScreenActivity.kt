package it.fast4x.riplay.extensions.ads.promo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yambo.music.R

private val YamboBlack = Color(0xFF0A0A0A)
private val YamboWhite = Color.White

class YamboPromoFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            YamboPromoFullScreenContent(
                onClose = { finish() },
                onConverted = {
                    YamboPromoManager.launchUpgradeFlow(this@YamboPromoFullScreenActivity)
                    finish()
                }
            )
        }
    }
}

@Composable
private fun YamboPromoFullScreenContent(
    onClose: () -> Unit,
    onConverted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YamboBlack)
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.18f,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 28.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            YamboBlack.copy(alpha = 0.55f),
                            YamboBlack.copy(alpha = 0.85f),
                            YamboBlack
                        )
                    )
                )
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.yambo_promo_fs_skip),
                tint = YamboWhite.copy(alpha = 0.8f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(YamboWhite.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.musical_note),
                    contentDescription = null,
                    tint = YamboWhite,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.yambo_promo_fs_title),
                color = YamboWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.yambo_promo_fs_subtitle),
                color = YamboWhite.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(36.dp))

            FeatureRow(
                iconRes = R.drawable.play,
                text = stringResource(R.string.yambo_promo_fs_feature_1)
            )
            Spacer(Modifier.height(16.dp))
            FeatureRow(
                iconRes = R.drawable.equalizer,
                text = stringResource(R.string.yambo_promo_fs_feature_2)
            )
            Spacer(Modifier.height(16.dp))
            FeatureRow(
                iconRes = R.drawable.download,
                text = stringResource(R.string.yambo_promo_fs_feature_3)
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onConverted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = YamboWhite,
                    contentColor = YamboBlack
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.yambo_promo_fs_cta),
                    color = YamboBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.yambo_promo_fs_skip),
                    color = YamboWhite.copy(alpha = 0.55f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(iconRes: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(YamboWhite.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = YamboWhite,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text = text,
            color = YamboWhite.copy(alpha = 0.92f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
