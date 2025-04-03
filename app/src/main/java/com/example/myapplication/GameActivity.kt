package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random
import android.graphics.Movie
import java.io.IOException
import java.io.InputStream

// Перечисление типов объектов с русскими названиями
enum class ObjectType(val score: Int) {
    ВИШНЯ(5),
    БАНАН(10),
    ВИНОГРАД(15),
    БОМБА(-30)
}

class GameActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {
    // Размеры объектов
    private val playerWidth = 200f
    private val playerHeight = 250f
    private val objectSize = 120f

    // Игровые параметры
    private var score = 0
    private var lives = 3
    private var gameSpeed = 1f
    private var difficultyLevel = 0
    private val difficultyInterval = 5000L
    private var gifStartTime: Long = 0

    // Контроль спавна
    private var lastSpawnTime = 0L
    private var spawnInterval = 1000L
    private var lastDifficultyIncreaseTime = 0L

    // Игровые объекты
    private val playerRect = RectF()
    private val fallingObjects = mutableListOf<FallingObject>()
    private var gameThread: Thread? = null
    private var isRunning = false
    private var isGameOver = false
    private val surfaceHolder: SurfaceHolder = holder
    private val paint = Paint().apply { isAntiAlias = true }

    // Графика
    private lateinit var backgroundBitmap: Bitmap
    private lateinit var playerGif: Movie
    private lateinit var grapeBitmap: Bitmap
    private lateinit var cherryBitmap: Bitmap
    private lateinit var bananaBitmap: Bitmap
    private lateinit var bombBitmap: Bitmap

    // Звуки
    private lateinit var soundPool: SoundPool
    private var fruitSoundId = 0
    private var bombSoundId = 0
    private var loseSoundId = 0
    private lateinit var backgroundMusic: MediaPlayer

    init {
        isFocusable = true
        holder.addCallback(this)
        loadGraphics()
        initSounds()
    }

    private fun loadGraphics() {
        try {
            backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.fon)

            val inputStream: InputStream = resources.openRawResource(R.drawable.personazh)
            playerGif = Movie.decodeStream(inputStream)
            inputStream.close()

            cherryBitmap = BitmapFactory.decodeResource(resources, R.drawable.vishnya)
                .let { Bitmap.createScaledBitmap(it, objectSize.toInt(), objectSize.toInt(), true) }

            bananaBitmap = BitmapFactory.decodeResource(resources, R.drawable.banan)
                .let { Bitmap.createScaledBitmap(it, objectSize.toInt(), objectSize.toInt(), true) }

            grapeBitmap = BitmapFactory.decodeResource(resources, R.drawable.vinograd)
                .let { Bitmap.createScaledBitmap(it, objectSize.toInt(), objectSize.toInt(), true) }

            bombBitmap = BitmapFactory.decodeResource(resources, R.drawable.bomba)
                .let { Bitmap.createScaledBitmap(it, objectSize.toInt(), objectSize.toInt(), true) }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun initSounds() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        fruitSoundId = soundPool.load(context, R.raw.fruit, 1)
        bombSoundId = soundPool.load(context, R.raw.bomba, 1)
        loseSoundId = soundPool.load(context, R.raw.lose, 1)

        backgroundMusic = MediaPlayer.create(context, R.raw.fon)
        backgroundMusic.isLooping = true
        backgroundMusic.setVolume(0.5f, 0.5f)
    }

    override fun run() {
        gifStartTime = System.currentTimeMillis()
        lastDifficultyIncreaseTime = System.currentTimeMillis()
        backgroundMusic.start()

        while (isRunning) {
            if (!surfaceHolder.surface.isValid) continue

            val currentTime = System.currentTimeMillis()
            if (!isGameOver) {
                update(currentTime)
            }

            drawFrame()

            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun update(currentTime: Long) {
        if (currentTime - lastDifficultyIncreaseTime > difficultyInterval) {
            increaseDifficulty()
            lastDifficultyIncreaseTime = currentTime
        }

        if (currentTime - lastSpawnTime > spawnInterval) {
            spawnRandomObject()
            lastSpawnTime = currentTime
        }

        val iterator = fallingObjects.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            obj.y += obj.speed * gameSpeed
            obj.rect.set(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)

            if (RectF.intersects(obj.rect, playerRect)) {
                handleCollision(obj.type)
                iterator.remove()
                continue
            }

            if (obj.y > height) {
                iterator.remove()
            }
        }
    }

    private fun increaseDifficulty() {
        difficultyLevel++
        gameSpeed = 1f + (difficultyLevel * 0.12f)
        spawnInterval = (1000L / gameSpeed).toLong().coerceAtLeast(500)
    }

    private fun spawnRandomObject() {
        val bombChance = 25 + (difficultyLevel * 1.5).toInt().coerceAtMost(40)
        val cherryChance = (35 - difficultyLevel).coerceAtLeast(15)
        val bananaChance = (30 - difficultyLevel).coerceAtLeast(15)
        val grapeChance = (25 - difficultyLevel).coerceAtLeast(10)

        val type = when (Random.nextInt(100)) {
            in 0 until cherryChance -> ObjectType.ВИШНЯ
            in cherryChance until cherryChance + bananaChance -> ObjectType.БАНАН
            in cherryChance + bananaChance until cherryChance + bananaChance + grapeChance -> ObjectType.ВИНОГРАД
            else -> ObjectType.БОМБА
        }

        val baseSpeed = when (type) {
            ObjectType.БОМБА -> Random.nextInt(15, 22)
            else -> Random.nextInt(8, 15)
        }

        val speed = baseSpeed * gameSpeed

        val bitmap = when (type) {
            ObjectType.ВИШНЯ -> cherryBitmap
            ObjectType.БАНАН -> bananaBitmap
            ObjectType.ВИНОГРАД -> grapeBitmap
            ObjectType.БОМБА -> bombBitmap
        }

        fallingObjects.add(FallingObject(
            x = Random.nextInt(0, width - objectSize.toInt()).toFloat(),
            y = -objectSize,
            width = objectSize,
            height = objectSize,
            speed = speed.toFloat(),
            type = type,
            bitmap = bitmap
        ))
    }

    private fun handleCollision(type: ObjectType) {
        score = (score + type.score).coerceAtLeast(0)
        when {
            type.score > 0 -> {
                soundPool.play(fruitSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
            }
            else -> {
                lives--
                soundPool.play(bombSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
                if (lives <= 0) {
                    isGameOver = true
                    soundPool.play(loseSoundId, 1.0f, 1.0f, 0, 0, 1.0f)
                    backgroundMusic.pause()
                    showGameOver()
                }
            }
        }
    }

    private fun drawFrame() {
        val canvas = surfaceHolder.lockCanvas()
        try {
            synchronized(surfaceHolder) {
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(backgroundBitmap, null, Rect(0, 0, width, height), paint)

                for (obj in fallingObjects) {
                    canvas.drawBitmap(obj.bitmap, obj.rect.left, obj.rect.top, paint)
                }

                val now = System.currentTimeMillis()
                playerGif.setTime((now - gifStartTime).toInt() % playerGif.duration())
                canvas.save()
                canvas.translate(playerRect.left, playerRect.top)
                canvas.scale(
                    playerRect.width() / playerGif.width(),
                    playerRect.height() / playerGif.height()
                )
                playerGif.draw(canvas, 0f, 0f)
                canvas.restore()

                paint.color = Color.WHITE
                paint.textSize = 50f
                canvas.drawText("Очки: $score", 30f, 80f, paint)
                canvas.drawText("Жизни: $lives", width - 250f, 80f, paint)

                if (isGameOver) {
                    paint.color = Color.RED
                    paint.textSize = 100f
                    val text = "ИГРА ОКОНЧЕНА"
                    val textWidth = paint.measureText(text)
                    canvas.drawText(text, width / 2f - textWidth / 2, height / 2f, paint)
                }
            }
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        playerRect.set(
            width / 2f - playerWidth / 2,
            height - playerHeight - 50f,
            width / 2f + playerWidth / 2,
            height - 50f
        )

        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this)
            gameThread?.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
    }

    private fun showGameOver() {
        (context as GameActivity).runOnUiThread {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                setBackgroundColor(Color.argb(220, 40, 40, 40))

                val title = TextView(context).apply {
                    text = "ИГРА ОКОНЧЕНА"
                    setTextColor(Color.RED)
                    textSize = 28f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }
                addView(title)

                val scoreText = TextView(context).apply {
                    text = "Ваш счет: $score"
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                }
                addView(scoreText)
            }

            AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Заново") { _, _ ->
                    resetGame()
                    backgroundMusic.start()
                }
                .setNegativeButton("В меню") { _, _ ->
                    (context as GameActivity).finish()
                }
                .setCancelable(false)
                .create()
                .also { dialog ->
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.show()

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 0, 150, 0))
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(100, 150, 0, 0))
                    }
                }
        }
    }

    private fun resetGame() {
        fallingObjects.clear()
        score = 0
        lives = 3
        difficultyLevel = 0
        gameSpeed = 1f
        spawnInterval = 1000L
        isGameOver = false
        gifStartTime = System.currentTimeMillis()
        lastDifficultyIncreaseTime = System.currentTimeMillis()
        backgroundMusic.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver) return false

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                playerRect.offsetTo(event.x - playerRect.width() / 2, playerRect.top)

                if (playerRect.left < 0) playerRect.offsetTo(0f, playerRect.top)
                if (playerRect.right > width) playerRect.offsetTo(width - playerRect.width(), playerRect.top)
                return true
            }
        }
        return true
    }

    private fun stopGame() {
        isRunning = false
        gameThread?.join()
        soundPool.release()
        backgroundMusic.release()
    }
}

data class FallingObject(
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    val speed: Float,
    val type: ObjectType,
    val bitmap: Bitmap
) {
    val rect = RectF(x, y, x + width, y + height)
}