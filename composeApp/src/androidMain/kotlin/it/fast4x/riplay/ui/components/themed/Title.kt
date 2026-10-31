package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

@Composable
fun Title(
    title: String,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    @DrawableRes icon: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            //.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick != null) {
                if (enableClick)
                    onClick?.invoke()
            }
            .padding(start = 16.dp, end = 12.dp, top = verticalPadding + 6.dp, bottom = verticalPadding)
    ) {
        // Yammbo Noir: big bold section title; the plain "more" arrow becomes a mono "SEE ALL".
        Text(
            text = title,
            style = noirTitleStyle(),
            modifier = Modifier.weight(1f)

        )

        if (onClick != null && enableClick) {
            if (icon == null || icon == R.drawable.arrow_forward)
                Text(
                    text = stringResource(R.string.noir_see_all).uppercase(),
                    style = typography().xxxs.mono.copy(color = colorPalette().text, fontSize = 11.sp)
                )
            else
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colorPalette().text,
                    modifier = Modifier.size(22.dp)
                )
        }
    }
}

@Composable
fun Title2Actions(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes icon1: Int? = R.drawable.arrow_forward,
    @DrawableRes icon2: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick1: (() -> Unit)? = null,
    onClick2: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick1 != null) {
                if (enableClick)
                    onClick1?.invoke()
            }
            .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = noirTitleStyle(),
            modifier = Modifier.weight(1f)

        )
        if (onClick2 != null && enableClick) {
            Icon(
                painter = painterResource(icon2 ?: R.drawable.arrow_forward),
                contentDescription = null,
                tint = colorPalette().text,
                modifier = Modifier
                    .clickable {
                        onClick2.invoke()
                    }
                    .padding(end = 12.dp)
                    .size(20.dp)
            )
        }

        if (onClick1 != null && enableClick) {
            Icon(
                painter = painterResource(icon1 ?: R.drawable.arrow_forward),
                contentDescription = null,
                tint = colorPalette().text,
                modifier = Modifier
                    .clickable {
                        onClick1.invoke()
                    }
                    .size(20.dp)
            )
        }

    }
}

@Composable
fun TitleSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = noirTitleStyle(),
        modifier = modifier.padding(end = 12.dp)

    )


}

@Composable
fun TitleMiniSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = typography().xxxs.mono.copy(color = colorPalette().textSecondary, fontSize = 11.sp),
        modifier = modifier.padding(top = 5.dp)
    )
}
@Composable
private fun noirTitleStyle(): TextStyle = typography().xl.tight.copy(
    color = colorPalette().text,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    textAlign = TextAlign.Start
)
