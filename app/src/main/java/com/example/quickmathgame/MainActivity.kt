package com.example.quickmathgame

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var questionTextView: TextView
    private lateinit var scoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var leaderboardTextView: TextView
    private lateinit var answerEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var playAgainButton: Button
    private lateinit var googleSignInButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1

    // Game variables
    private var score = 0
    private var comboCount = 0
    private var difficultyLevel = 1
    private var timeLimit = 30000L
    private var timeLeft = timeLimit
    private var correctAnswer = 0
    private val operators = listOf("+", "-", "*")
    private lateinit var timer: CountDownTimer
    private lateinit var correctSound: MediaPlayer
    private lateinit var incorrectSound: MediaPlayer
    private lateinit var preferences: SharedPreferences
    private var highScore = 0
    private var consecutiveCorrectCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        questionTextView = findViewById(R.id.questionTextView)
        scoreTextView = findViewById(R.id.scoreTextView)
        timerTextView = findViewById(R.id.timerTextView)
        leaderboardTextView = findViewById(R.id.leaderboardTextView)
        answerEditText = findViewById(R.id.answerEditText)
        submitButton = findViewById(R.id.submitButton)
        playAgainButton = findViewById(R.id.playAgainButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        auth = FirebaseAuth.getInstance()

        correctSound = MediaPlayer.create(this, R.raw.correct_sound)
        incorrectSound = MediaPlayer.create(this, R.raw.incorrect_sound)

        preferences = getSharedPreferences("game_prefs", MODE_PRIVATE)
        highScore = preferences.getInt("high_score", 0)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInButton.setOnClickListener {
            signIn()
        }

        startButton.setOnClickListener {
            startButton.visibility = Button.GONE
            showGameUI()
            startGame()
        }

        submitButton.setOnClickListener {
            checkAnswer()
        }

        playAgainButton.setOnClickListener {
            playAgainButton.visibility = Button.GONE
            startGame()
        }
    }

    private fun startGame() {
        score = 0
        comboCount = 0
        difficultyLevel = 1
        timeLimit = 30000L
        timeLeft = timeLimit
        consecutiveCorrectCount = 0
        updateScore()
        generateQuestion()
        startTimer()
        submitButton.isEnabled = true
    }

    private fun showGameUI() {
        scoreTextView.visibility = TextView.VISIBLE
        timerTextView.visibility = TextView.VISIBLE
        questionTextView.visibility = TextView.VISIBLE
        answerEditText.visibility = EditText.VISIBLE
        submitButton.visibility = Button.VISIBLE
        leaderboardTextView.visibility = TextView.VISIBLE
    }

    private fun generateQuestion() {
        val number1 = Random.nextInt(1, 10 * difficultyLevel)
        val number2 = Random.nextInt(1, 10 * difficultyLevel)
        val operator = operators.random()

        correctAnswer = when (operator) {
            "+" -> number1 + number2
            "-" -> number1 - number2
            "*" -> number1 * number2
            else -> 0
        }
        questionTextView.text = "$number1 $operator $number2"
        answerEditText.text.clear()

        // Increase difficulty based on score
        if (score % 50 == 0 && score > 0) {
            difficultyLevel++
            timeLimit = maxOf(10000L, timeLimit - 5000L)
            timer.cancel()
            startTimer()
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                updateTimer()
            }

            override fun onFinish() {
                gameOver()
            }
        }.start()
    }

    private fun checkAnswer() {
        val answer = answerEditText.text.toString().toIntOrNull()
        if (answer == correctAnswer) {
            comboCount++
            consecutiveCorrectCount++
            val comboBonus = if (comboCount >= 3) 5 else 0
            score += 10 + comboBonus
            updateScore()
            correctSound.start()

            if (consecutiveCorrectCount == 5) {
                timeLeft += 20000L
                timer.cancel()
                startTimer()
                consecutiveCorrectCount = 0
                Toast.makeText(this, "Bonus Time! +20 seconds", Toast.LENGTH_SHORT).show()
            }

            generateQuestion()
        } else {
            comboCount = 0
            consecutiveCorrectCount = 0
            incorrectSound.start()
            answerEditText.error = "Incorrect! Try again."
        }
    }

    private fun updateScore() {
        scoreTextView.text = "Score: $score"
    }

    private fun updateTimer() {
        val secondsLeft = (timeLeft / 1000).toInt()
        timerTextView.text = "Time: $secondsLeft"
    }

    private fun updateLeaderboard() {
        var scores = preferences.getStringSet("leaderboard", mutableSetOf())?.toMutableList() ?: mutableListOf()
        scores.add(score.toString())
        scores.sortByDescending { it.toInt() }
        if (scores.size > 3) scores = scores.take(3).toMutableList()
        preferences.edit().putStringSet("leaderboard", scores.toSet()).apply()
        leaderboardTextView.text = "Leaderboard:\n" + scores.joinToString("\n")
    }

    private fun gameOver() {
        if (score > highScore) {
            highScore = score
            preferences.edit().putInt("high_score", highScore).apply()
            Toast.makeText(this, "New High Score!", Toast.LENGTH_SHORT).show()
        }
        updateLeaderboard()
        timerTextView.text = "Time's up!"
        submitButton.isEnabled = false
        playAgainButton.visibility = Button.VISIBLE
        questionTextView.text = "Game Over! Your score: $score\nHigh Score: $highScore"
    }

    override fun onDestroy() {
        super.onDestroy()
        correctSound.release()
        incorrectSound.release()
    }
    private fun signIn() {
        val signInIntent: Intent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            // Sign-in failed
            updateUI(null)
            Toast.makeText(this, "Sign-In Failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign-in success
                        val user = auth.currentUser
                        updateUI(user)
                    } else {
                        // Sign-in failed, get the error
                        val exception = task.exception
                        Toast.makeText(this, "Firebase Authentication Failed: ${exception?.message}", Toast.LENGTH_SHORT).show()
                        updateUI(null)
                    }
                }
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Toast.makeText(this, "Welcome, ${user.displayName}", Toast.LENGTH_SHORT).show()
            leaderboardTextView.visibility = TextView.VISIBLE
            leaderboardTextView.text = "User: ${user.displayName}\n" + leaderboardTextView.text
        } else {
            leaderboardTextView.visibility = TextView.GONE
        }
    }

    // Rest of the game code (unchanged)
}
