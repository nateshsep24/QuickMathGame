package com.example.quickmathgame

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

    private lateinit var signInWithGoogleButton: Button
    private lateinit var guestButton: Button
    private lateinit var profileIcon: ImageView
    private lateinit var settingsIcon: ImageView
    private lateinit var welcomeTextView: TextView
    private lateinit var startGameButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var questionTextView: TextView
    private lateinit var scoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var leaderboardTextView: TextView
    private lateinit var answerEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var playAgainButton: Button
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

        // Initialize views
        signInWithGoogleButton = findViewById(R.id.signInWithGoogleButton)
        guestButton = findViewById(R.id.guestButton)
        profileIcon = findViewById(R.id.profileIcon)
        settingsIcon = findViewById(R.id.settingsIcon)
        welcomeTextView = findViewById(R.id.welcomeTextView)
        startGameButton = findViewById(R.id.startGameButton)
        questionTextView = findViewById(R.id.questionTextView)
        scoreTextView = findViewById(R.id.scoreTextView)
        timerTextView = findViewById(R.id.timerTextView)
        leaderboardTextView = findViewById(R.id.leaderboardTextView)
        answerEditText = findViewById(R.id.answerEditText)
        submitButton = findViewById(R.id.submitButton)
        playAgainButton = findViewById(R.id.playAgainButton)

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

        signInWithGoogleButton.setOnClickListener {
            signIn()
        }

        guestButton.setOnClickListener {
            setupGuestUser()
            displayGameUI()
            startGame() // Start the game as a guest
        }

        profileIcon.setOnClickListener {
            showMenu()
        }

        startGameButton.setOnClickListener {
            displayGameUI()
            startGame() // Start the game when logged in
        }

        submitButton.setOnClickListener {
            checkAnswer()
        }

        playAgainButton.setOnClickListener {
            playAgainButton.visibility = Button.GONE
            startGame()
        }
    }

    private fun signIn() {
        val signInIntent: Intent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun setupGuestUser() {
        displayUserInterface("Guest")
        saveUserLocally("Guest")
    }

    private fun saveUserLocally(userName: String) {
        val editor = preferences.edit()
        editor.putString("last_logged_in_user", userName)
        editor.apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Sign-In Failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        displayUserInterface(user?.displayName ?: "User")
                        saveUserLocally(user?.displayName ?: "User")
                    } else {
                        Toast.makeText(this, "Firebase Authentication Failed", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun displayUserInterface(userName: String) {
        signInWithGoogleButton.visibility = View.GONE
        guestButton.visibility = View.GONE

        profileIcon.visibility = View.VISIBLE
        settingsIcon.visibility = View.VISIBLE
        welcomeTextView.visibility = View.VISIBLE
        startGameButton.visibility = View.VISIBLE

        welcomeTextView.text = "Welcome, $userName!"
    }

    private fun showMenu() {
        val options = arrayOf("High Score", "Logout")
        val builder = AlertDialog.Builder(this)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> showHighScores()
                1 -> logoutUser()
            }
        }
        builder.show()
    }

    private fun showHighScores() {
        val highScores = getTop10HighScores() // Placeholder function for high scores retrieval
        val highScoresText = highScores.joinToString("\n") { "Score: $it" }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Top 10 High Scores")
        builder.setMessage(highScoresText)
        builder.setPositiveButton("Close") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun logoutUser() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            // Reset UI to initial state
            profileIcon.visibility = View.GONE
            settingsIcon.visibility = View.GONE
            welcomeTextView.visibility = View.GONE
            startGameButton.visibility = View.GONE

            signInWithGoogleButton.visibility = View.VISIBLE
            guestButton.visibility = View.VISIBLE
        }
    }

    private fun getTop10HighScores(): List<Int> {
        // Placeholder function - Replace with actual high score logic
        return listOf(100, 95, 90, 85, 80, 75, 70, 65, 60, 55)
    }

    private fun startGame() {
        // Implement the game start logic here
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

    private fun displayGameUI() {
        scoreTextView.visibility = View.VISIBLE
        timerTextView.visibility = View.VISIBLE
        questionTextView.visibility = View.VISIBLE
        answerEditText.visibility = View.VISIBLE
        submitButton.visibility = View.VISIBLE
        leaderboardTextView.visibility = View.VISIBLE
    }

    private fun updateScore() {
        scoreTextView.text = "Score: $score"
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

    private fun updateTimer() {
        val secondsLeft = (timeLeft / 1000).toInt()
        timerTextView.text = "Time: $secondsLeft"
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

    private fun updateLeaderboard() {
        var scores = preferences.getStringSet("leaderboard", mutableSetOf())?.toMutableList() ?: mutableListOf()
        scores.add(score.toString())
        scores.sortByDescending { it.toInt() }
        if (scores.size > 3) scores = scores.take(3).toMutableList()
        preferences.edit().putStringSet("leaderboard", scores.toSet()).apply()
        leaderboardTextView.text = "Leaderboard:\n" + scores.joinToString("\n")
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

    override fun onDestroy() {
        super.onDestroy()
        correctSound.release()
        incorrectSound.release()
    }
}
