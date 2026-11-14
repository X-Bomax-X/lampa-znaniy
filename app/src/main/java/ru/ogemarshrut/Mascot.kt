package ru.ogemarshrut

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun Mascot(
    modifier: Modifier = Modifier,
    reaction: AnswerOutcome? = null,
    thumbsUp: Boolean = false,
    waving: Boolean = false,
    waiting: Boolean = false
) {
    val phase = remember(reaction) { Animatable(if (reaction == null) 1f else 0f) }
    LaunchedEffect(reaction) {
        if (reaction != null) {
            phase.animateTo(1f, tween(
                durationMillis = if (reaction == AnswerOutcome.CORRECT) 720 else 640,
                easing = LinearEasing
            ))
        }
    }
    val wave = remember { Animatable(0f) }
    val celebration = if (thumbsUp) {
        val time = remember { Animatable(0f) }
        LaunchedEffect(Unit) { time.animateTo(1450f, tween(1450, easing = LinearEasing)) }
        time
    } else null
    LaunchedEffect(waving) {
        if (waving) wave.animateTo(1f, tween(1550, easing = LinearEasing))
        else wave.snapTo(0f)
    }
    val idle = if (waiting) {
        rememberInfiniteTransition(label = "Ожидание").animateFloat(
            initialValue = 0f,
            targetValue = 2800f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
            label = "Анимация ожидания"
        )
    } else null
    Canvas(modifier) {
        if (celebration != null) {
            withTransform({ scale(size.width / 120f, size.height / 120f, pivot = Offset.Zero) }) {
                drawCelebration(celebration.value)
            }
            return@Canvas
        }
        val elapsed = phase.value
        val active = reaction?.takeIf { elapsed < 1f }
        val happy = active == AnswerOutcome.CORRECT
        val concerned = active == AnswerOutcome.INCORRECT
        val lift = if (active == null) 0f else minOf(elapsed / .25f, 1f, (1 - elapsed) / .28f)
        val gesture = lift * (.88f + .12f * sin(elapsed * (4 * PI).toFloat()))
        val hop = if (active == AnswerOutcome.CORRECT) -7f * lift else 0f
        val tilt = if (concerned) -10f * gesture else 0f
        val wavingPhase = wave.value
        val waveAmount = minOf(wavingPhase / .18f, 1f, (1f - wavingPhase) / .20f)
        val waveAngle = waveAmount * (-116f + 18f * sin(wavingPhase * (6 * PI).toFloat()))
        val idleTime = idle?.value
        withTransform({
            scale(size.width / 120f, size.height / 120f, pivot = Offset.Zero)
            if (idleTime != null) scale(.89f, .89f, pivot = Offset(59f, 106f))
            translate(top = hop)
            rotate(tilt, pivot = Offset(60f, 84f))
        }) {
            drawBulb(happy, concerned, gesture, waveAngle, idleTime)
        }
    }
}

private val Outline = Color(0xFFD88516)
private val Limb = Color(0xFFFFC84B)
private val FaceInk = Color(0xFF252630)
private val GloveOutline = Color(0xFFBB741C)
private val GloveCrease = Color(0xFFD6D8DF)

private fun path(value: String) = PathParser().parsePathString(value).toPath()

private val Rays = path("M32 18l-5-6 M61 12V5 M88 19l6-6 M18 38l-9-3 M100 35l8-3")
private val LeftLeg = path("M39 84L28 100")
private val RightLeg = path("M74 84L86 100")
private val LeftShoe = path("M23 97Q12 99 12 107Q13 114 31 113Q42 112 39 104L34 97Z")
private val RightShoe = path("M81 97L88 97Q102 99 104 107Q103 114 87 113Q76 113 76 105Z")
private val LeftShine = path("M18 105Q23 102 28 102")
private val RightShine = path("M86 102Q93 102 97 106")
private val Glass = path("M58 17C38 17 24 30 24 49C24 65 35 74 42 82L45 93H71L75 81C85 71 94 62 94 47C94 29 80 17 58 17Z")
private val GlassShine = path("M34 39Q40 26 53 25")
private val Socket = path("M44 83Q56 83 73 81Q77 81 77 85Q78 89 73 90L43 92Q40 91 40 87Q40 84 44 83Z M44 92L73 90Q74 95 68 99Q56 105 48 98Z")
private val SocketShine = path("M46 87L71 85")
private val HappyEyes = path("M40 50Q46 42 52 50 M65 48Q71 40 77 48")
private val Brows = path("M41 37Q46 35 51 37 M66 34Q71 32 76 34")
private val ConcernedBrows = path("M39 38L50 34 M66 32L77 36")
private val Smile = path("M53 62Q59 67 65 61")
private val Grin = path("M50 58Q60 62 70 55Q68 72 59 72Q52 72 50 58Z")
private val Tongue = path("M54 67Q59 62 66 66Q61 75 54 67Z")
private val ConcernedMouth = path("M53 66Q59 60 66 65")

private val LeftArm = path("M0 0Q-9 4 -12 17")
private val LeftGlove = path("M-12 11Q-17 10 -19 14Q-21 17 -18 20Q-21 24 -17 28Q-12 33 -6 29Q0 27 -2 21Q-4 16 -8 16Q-8 12 -12 11Z")
private val LeftCrease = path("M-16 22Q-14 20 -11 21")
private val RightArm = path("M0 0Q10 5 12 19")
private val RightGlove = path("M12 12Q16 10 18 14Q20 17 17 20Q21 23 19 27Q17 31 12 32Q6 32 4 27Q2 23 5 19L8 17Q8 13 12 12Z")
private val RightCrease = path("M16 23Q13 21 11 23")

private val ThumbsUpGlove = path(
    "M14 15H0Q-6 15 -11 13Q-16 13 -17 9Q-17 7 -15 6" +
        "Q-20 5 -20 1Q-20 -2 -17 -3Q-21 -4 -21 -8Q-21 -11 -17 -12" +
        "Q-19 -16 -15 -18Q-13 -19 -8 -19H-3Q1 -21 2 -25L3 -29" +
        "Q4 -33 8 -32Q13 -31 13 -26L12 -19Q11 -15 14 -13" +
        "Q19 -10 19 -4V8Q19 15 14 15Z"
)
private val ThumbsUpCreases = path(
    "M-17 -12Q-12 -10 -7 -11 M-17 -3Q-12 -1 -7 -2 M-15 6Q-11 8 -6 7 M-3 -19Q1 -19 4 -16"
)

private fun DrawScope.drawCelebration(time: Float) {
    val windup = idleBeat(time, 50, 220, 290, 460)
    val turn = idleBeat(time, 235, 370, 425, 625)
    val thrust = idleEase(time, 485, 820)
    val bounce = idleBeat(time, 570, 760, 800, 1020)
    val lean = 12f * windup - 8f * thrust
    withTransform({
        scale(.91f, .91f, pivot = Offset(60f, 106f))
        translate(top = 5f * windup - 7f * bounce)
        rotate(lean, pivot = Offset(58f, 86f))
        scale(1f - .42f * turn, 1f, pivot = Offset(58f, 86f))
    }) {
        drawBulb(happy = thrust > .18f, concerned = false, gesture = .25f * thrust, wave = 0f,
            idle = null, hideLeftArm = time >= 485f)
        if (time >= 485f) {
            val handX = 22f + 2f * thrust
            val handY = 84f - 23f * thrust
            val gloveSize = .62f + .56f * thrust + .13f * bounce
            val arm = Path().apply {
                moveTo(32f, 62f)
                quadraticTo(36f, 81f - 3f * thrust, handX + 13f * gloveSize, handY + 7f * gloveSize)
            }
            leg(arm)
            withTransform({
                translate(left = handX, top = handY)
                rotate(15f - 11f * thrust, pivot = Offset.Zero)
                scale(gloveSize, gloveSize, pivot = Offset.Zero)
            }) {
                drawPath(ThumbsUpGlove, Color.White)
                stroke(ThumbsUpGlove, GloveOutline, 2f)
                stroke(ThumbsUpCreases, GloveCrease, 1.5f)
            }
        }
    }
}

private fun pointingGlove(reach: Float): Path = Path().apply {
    val finger = 14f * reach
    moveTo(12f, 12f)
    quadraticTo(16f, 10f, 18f, 14f)
    quadraticTo(20f, 17f, 17f, 20f)
    cubicTo(20f + finger, 20f - 4f * reach, 24f + finger, 26f - 4f * reach, 19f + finger, 27f - 4f * reach)
    quadraticTo(17f, 31f, 12f, 32f)
    quadraticTo(6f, 32f, 4f, 27f)
    quadraticTo(2f, 23f, 5f, 19f)
    lineTo(8f, 17f)
    quadraticTo(8f, 13f, 12f, 12f)
    close()
}

// The heel stays planted. Each beat lifts the toe, holds it briefly, then lands faster.
private fun idleEase(time: Float, first: Int, last: Int): Float =
    FastOutSlowInEasing.transform(((time - first) / (last - first)).coerceIn(0f, 1f))

private fun idleBeat(time: Float, first: Int, peak: Int, release: Int, last: Int): Float =
    idleEase(time, first, peak) * (1f - idleEase(time, release, last))

private fun DrawScope.drawBulb(
    happy: Boolean, concerned: Boolean, gesture: Float, wave: Float, idle: Float?, hideLeftArm: Boolean = false
) {
    val weight = idle?.let { idleBeat(it, 285, 520, 2380, 2680) } ?: 0f
    val tapTime = ((idle ?: 0f) - 620f) % 292f
    val tap = if (idle != null && idle in 620f..<1788f) idleBeat(tapTime, 0, 76, 116, 184) else 0f
    val glance = idle?.let { idleBeat(it, 1780, 1960, 2440, 2690) } ?: 0f
    val point = idle?.let { idleBeat(it, 1920, 2090, 2350, 2560) } ?: 0f
    val blink = idle?.let {
        maxOf(idleBeat(it, 225, 270, 280, 330), idleBeat(it, 1750, 1792, 1802, 1854),
            idleBeat(it, 2570, 2614, 2622, 2670))
    } ?: 0f
    val bodyX = -5f * weight + 2f * glance
    val bodyY = 2f * weight - 2f * point
    val lean = -5f * weight + 7f * glance
    val armFlick = point * sin(((idle ?: 0f) - 1920f) * (2f * PI / 320f).toFloat()) * 8f
    ellipse(59f, 110f, 42f, 5f, Color(0x33E9B52A))
    if (idle == null) {
        leg(LeftLeg)
        leg(RightLeg)
        shoe(RightShoe, RightShine)
    } else {
        leg(Path().apply {
            moveTo(39f + bodyX, 84f + bodyY)
            quadraticTo(31f - 3f * weight, 90f + bodyY, 28f, 100f)
        })
        leg(Path().apply {
            moveTo(74f + bodyX, 84f + bodyY)
            quadraticTo(77f + 3f * weight, 92f, 85f + 6f * weight, 100f - tap)
        })
        withTransform({
            translate(left = 6f * weight)
            rotate(-18f * tap, pivot = Offset(80f, 111f))
        }) {
            shoe(RightShoe, RightShine)
        }
    }
    shoe(LeftShoe, LeftShine)
    withTransform({
        translate(bodyX, bodyY)
        rotate(lean, pivot = Offset(57f, 86f))
    }) {
        stroke(Rays, if (happy) Color(0xFFFFAF00) else Color(0xFFF2AA25),
            3.7f + if (happy) 2.3f * gesture else 0f)
        if (!hideLeftArm) {
            arm(32f + 7f * point, 63f, (if (happy) 40f else 30f) * gesture - 21f * weight + 128f * point,
                LeftArm, LeftGlove, LeftCrease)
        }
        arm(85f, 63f, (if (happy) -115f else -39f) * gesture + wave + 22f * weight - 70f * point + armFlick,
            RightArm, if (point > 0f) pointingGlove(point) else RightGlove, RightCrease)
        drawFace(happy, concerned, weight, glance, blink)
    }
}

private fun DrawScope.leg(outline: Path) {
    stroke(outline, Outline, 8f)
    stroke(outline, Limb, 5f)
}

private fun DrawScope.drawFace(happy: Boolean, concerned: Boolean, weight: Float, glance: Float, blink: Float) {
    drawPath(Glass, if (concerned) Color(0xFFF5C353) else Color(0xFFFFD746))
    stroke(Glass, Outline, 2.6f)
    stroke(GlassShine, Color(0xFFFFF2A1), 5f)
    drawPath(Socket, Color(0xFF9DA5B7))
    stroke(Socket, Color(0xFF434854), 2f)
    stroke(SocketShine, Color(0xFFE1E6EF), 2f)
    ellipse(39f, 60f, 5f, 2.8f, Color(0xFFF78964))
    ellipse(77f, 57f, 5f, 2.8f, Color(0xFFF78964))

    if (happy) {
        stroke(HappyEyes, FaceInk, 3f)
        drawPath(Grin, FaceInk)
        drawPath(Tongue, Color(0xFFFF7B86))
    } else {
        val turn = 4f * weight - 9f * glance
        val gaze = (if (concerned) -1f else 2f) + weight - glance * 3f
        withTransform({ translate(left = turn) }) {
            eye(46f, 49f, 7.8f, gaze, blink)
            eye(71f, 46f, 7.6f - 1.2f * weight, gaze, blink)
            stroke(if (concerned) ConcernedBrows else Brows, FaceInk, 2f)
            val mouth = when {
                concerned -> ConcernedMouth
                weight == 0f -> Smile
                else -> Path().apply {
                    moveTo(53f + weight, 62f + weight)
                    quadraticTo(59f, 67f - 5f * weight, 65f - weight, 61f + weight)
                }
            }
            stroke(mouth, FaceInk, 2.2f)
        }
    }
}

private fun DrawScope.eye(x: Float, y: Float, width: Float, gaze: Float, blink: Float) {
    val open = 1f - blink
    ellipse(x, y, width, 1f + 9f * open, Color(0xFFFFFDF6))
    ellipse(x + gaze, y, 4.4f, 1f + 5f * open, FaceInk)
    if (open > .35f) ellipse(x + gaze - 1f, y - 3f * open, 1.4f, 1.6f * open, Color.White)
}

private fun DrawScope.shoe(outline: androidx.compose.ui.graphics.Path, shine: androidx.compose.ui.graphics.Path) {
    drawPath(outline, FaceInk)
    stroke(outline, Color(0xFF14151A), 2f)
    stroke(shine, Color(0xFF5E6070), 2f)
}

private fun DrawScope.arm(
    x: Float,
    y: Float,
    angle: Float,
    limb: androidx.compose.ui.graphics.Path,
    glove: androidx.compose.ui.graphics.Path,
    crease: androidx.compose.ui.graphics.Path
) {
    withTransform({
        translate(x, y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        stroke(limb, Outline, 8f)
        stroke(limb, Limb, 5f)
        drawPath(glove, Color.White)
        stroke(glove, GloveOutline, 2f)
        stroke(crease, GloveCrease, 1.35f)
    }
}

private fun DrawScope.stroke(path: androidx.compose.ui.graphics.Path, color: Color, width: Float) {
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.ellipse(x: Float, y: Float, rx: Float, ry: Float, color: Color) {
    drawOval(color, topLeft = Offset(x - rx, y - ry), size = Size(rx * 2, ry * 2))
}
