// Note: will need to create or edit file every second to protect data
// Icon made with money image found here: https://www.pngarts.com/files/3/Falling-Cash-Money-PNG-Photo.png
package com.example.payvis

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.google.gson.Gson

data class Time(val initialTime: LocalDateTime){
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val startTime = initialTime.format(formatter)
    var timeAsSeconds: Double = 0.0  // This will be replaced by a value after init

    // The following values are slices from startTime string. I can think of a better way
    // to do it, but this is already here and it works so it'll stay
    val year = startTime.slice(0..3).toInt()  // here just in case I need it
    val month = startTime.slice(5..6).toInt()
    val day = startTime.slice(8..9).toInt()
    val hour = startTime.slice(11..12).toInt()
    val minute = startTime.slice(14..15).toInt()
    val second = startTime.slice(17..18).toInt()

    val dbEntryTag = startTime.split(" ")[0]  // Grabs the year, month, and day

    init {
        // To get the time as seconds, total up the num sec in month, day, ... sec
        val monthSec = this.month.toDouble() * 2628002.88
        val daySec = this.day.toDouble() * 86400.0
        val hourSec = this.hour * 3600.0
        val minSec = this.minute * 60.0
        val secSec = this.second.toDouble()

        this.timeAsSeconds = (monthSec + daySec + hourSec + minSec + secSec)
    }

    fun getElapsedTimeSec(endTime: LocalDateTime): Double{
        // This returns the elapsed time between the initialTime and endTime LocalDateTime

        val end = Time(endTime).timeAsSeconds
        return (end - this.timeAsSeconds)
    }

    fun statement(timePassed: Number, timeUnit: String): String{

        val singular = when (timeUnit){
            "hour" -> "hour"
            "min" -> "minute"
            "sec" -> "second"
            else -> "$timeUnit"
        }

        val plural = when(timeUnit){
            "hour" -> "hours"
            "min" -> "minutes"
            "sec" -> "seconds"
            else -> "${timeUnit}s"
        }

        var timeText: String

        if (timePassed.toInt() > 1) {timeText = "$timePassed $plural"}  // If there's more than one, it should be plural
        else if (timePassed.toInt() == 1) {timeText = "$timePassed $singular"}
        else {timeText = ""}

        return timeText
    }

    fun getElapsedTimeAsString(endTime: LocalDateTime): String{
        // Returns the time that elapsed between start and endTime as string

        // 1. Get the hour and minute count of time passed
        val elapsed = this.getElapsedTimeSec(endTime)

        // 1.01  Get around bug that occurs with time < 1 min
        if (elapsed < 60 && elapsed > 0){
            return "${this.statement(elapsed.toInt(), "sec")} worked"
        }
        else if (elapsed < 1 && elapsed > -1){  // Catch infinitesimals
            return "No time passed"
        }

        var timeElapsed = ((elapsed / 60.0) / 60.0).toString().split(".")  //place 0 = hours, 1 = minutes
        val hours =  timeElapsed[0].toInt()
        var minutes = ("." + timeElapsed[1]).toDouble() * 60  // re-add the period before converting to d

        // 1.1 Format minutes to have at most 2 decimal places
        minutes = String.format("%.02f", minutes).toDouble()

        // 2. Create two strings with the hour and minute data
        var hourText: String
        var minText: String

        hourText = this.statement(hours, "hour")
        minText = this.statement(minutes, "min")


        // 3. Compile final string
        if (hours == 0 && minutes == 0.0){return ""}
        else if (hours == 0 && minutes > 0.0){return "$minText worked"}
        else if (hours > 0 && minutes == 0.0) {return "$hourText worked"}
        else {return "$hourText and $minText worked"}
    }
}

data class Clock(val startTime: Time){
    var totalSeconds: Double = 0.0
    var sessionSeconds: Double = 0.0
    var active = false
    var dataBase: MutableMap<String, Map<String, String>> = mutableMapOf()  // day : total sec, pay, start, and end times
    var rate: Double = 0.0
    var pay: Double = 0.0

    fun update(): LocalDateTime{
        val now = LocalDateTime.now()
        this.sessionSeconds = startTime.getElapsedTimeSec(now)
        return now
    }

    fun stop(){
        this.active = false
    }

    fun calculatePay(payRate: Double, secondsWorked: Double): Double{
        val payPerSec = ((payRate / 60.0) / 60.0)
        return (payPerSec * secondsWorked)
    }

    fun createDBEntry(payRate: Double){
        // This will create a map entry containing time worked and pay earned

        // 1. Get entry key
        val day = startTime.dbEntryTag

        // 2. Create entry value
        val end = this.update()  // Grabs the finishing time
        val pay = this.calculatePay(payRate, this.totalSeconds + this.sessionSeconds)
        val data: Map<String, String> = mapOf(
            "seconds" to this.totalSeconds.toString(),
            "pay" to pay.toString(),  // This allows pay to accumulate over sessions
            "start" to this.startTime.startTime.toString(),
            "finish" to end.toString()
        )

        // 3. Create entry in db
        dataBase.put(day, data)
    }

    fun makeJsonString(): String{
        // Creates a json string of the database mutable map
        val gson = Gson()
        val dbString: String = gson.toJson(this.dataBase, MutableMap::class.java)
        return dbString
    }

    fun loadJsonString(data: String){
        val gson = Gson()
        val db = gson.fromJson(data, MutableMap::class.java)
    }
}


class MainActivity : AppCompatActivity() {

    // ============ FUNCTIONS ============

    fun goTo(url: String){
        // Opens browser pointing to url passed
        // https://stackoverflow.com/questions/5026349/how-to-open-a-website-when-a-button-is-clicked-in-android-application
        val uriUrl = Uri.parse(url)
        ContextCompat.startActivity(this, Intent(Intent.ACTION_VIEW, uriUrl), null)
    }

    fun saveFile(content: String, fileName: String){
        File(applicationContext.filesDir, fileName).printWriter().use{
            out -> out.println(content)
        }
    }

    fun readFile(fileName: String): String{
        File(applicationContext.filesDir, fileName).bufferedReader().use{
            return it.readText()
        }
    }

    fun clockFileExists(): Boolean{
        // Determines if the clock file has been created
        return File("clock.pvcf").exists()
    }

    fun clockFileFormattedCorrect(): Boolean{
        // Determines if the clock file was correctly formatted
        // If the data can be safely extracted, it should be formatted correctly
        try{
            val clockData = readFile("clock.pvcf").split("\n")
            val timeStamp = LocalDateTime.parse(clockData[0])
            val totalSec = clockData[1].toDouble()
            val active = clockData[2].toBoolean()
            val rate = clockData[3].toDouble()
            val sessionSeconds = clockData[4].toDouble()

            println("[i] Clock file formatted correctly")
            return true

        } catch (t: Throwable){
            println("[X] Clock file formatted incorrectly")
            return false
        }
    }

    fun createClockFile(clock: Clock){
        val timeStamp = clock.startTime.initialTime.toString()
        val totalSeconds = clock.totalSeconds
        val active = clock.active
        val rate = clock.rate
        val sessionSeconds = clock.sessionSeconds
        val dataStr: String = "$timeStamp\n$totalSeconds\n$active\n$rate\n$sessionSeconds"

        try{
            this.saveFile(dataStr, "clock.pvcf")
        } catch (t: Throwable){
            println("[X] Clock file failed to save correctly")
        }


    }

    fun loadClockFile(): Clock{
        /*
        Opens the clock file, grabs the data within, and creates a new
        Clock object with it. Returns this new object
         */

        // 1. Get data from file
        val clockData = readFile("clock.pvcf").split("\n")
        val timeStamp = LocalDateTime.parse(clockData[0])
        val totalSec = clockData[1].toDouble()
        val active = clockData[2].toBoolean()
        val rate = clockData[3].toDouble()
        val sessionSeconds = clockData[4].toDouble()

        // 2. Create objects
        val startTime: Time = Time(timeStamp)
        val newClock: Clock = Clock(startTime)
        newClock.totalSeconds = totalSec
        newClock.active = active
        newClock.rate = rate
        newClock.sessionSeconds = sessionSeconds

        return newClock
    }

    fun saveDBFile(clock: Clock){
        // Grabs the mutable map json string from Clock object and saves it to a file
        val dbJson = clock.makeJsonString()
        saveFile(dbJson, "workDB.json")
    }

    fun loadDBFile(clock: Clock){
        val dbJson = readFile("workDB.json")
        clock.loadJsonString(dbJson)
    }

    fun clockActive(): Boolean{
        // Determines if clock active
        val clockData = readFile("clock.pvcf").split("\n")
        try{
            return clockData[2].toBoolean()
        } catch (t: Throwable){
            return false
        }
    }


    // ============ APP ============
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Connect elements from app
        val startButton = findViewById<Button>(R.id.start_button)
        val resetButton = findViewById<Button>(R.id.reset_time_button)
        val displayPayButton = findViewById<Button>(R.id.display_pay_button)
        val exportDataButton = findViewById<Button>(R.id.export_button)

        val notifyView = findViewById<TextView>(R.id.notify_view)
        val timeWorked = findViewById<TextView>(R.id.time_worked_view)
        val payView = findViewById<TextView>(R.id.pay_view)

        val rateEntry = findViewById<EditText>(R.id.current_wage_entry)

        // Clear default text
        notifyView.text = ""
        timeWorked.text = ""
        payView.text = ""

        // Determine if clock file exists
        var clockStarted: Boolean  // If this remains false, app will make a new clock file at start
        var clock: Clock = Clock(Time(LocalDateTime.now()))  // Set equal to a place holder
        var date = Time(LocalDateTime.now()).startTime.split(" ")[0]
        // The above gets the current time and grabs the date section (time and date separated by space)
        var fileDate: String  // This is the date found in the file

        if (clockFileExists() && clockFileFormattedCorrect() && clockActive()) {  // Active clock
            // Mark clock started and import settings
            clock = loadClockFile()
            rateEntry.setText("${String.format("%.02f", clock.rate)}")  // Loads saved rate to entry
            clockStarted = true

        } else if (clockFileExists() && clockFileFormattedCorrect() && !clockActive()){
            // If the file is good and ready, but the clock was stopped

            // 1. Load permanent data
            clock = loadClockFile()
            rateEntry.setText(String.format("%.02f", clock.rate))
            fileDate = clock.startTime.startTime.split(" ")[1]

            // 2. If last active was today, keep total seconds
            if (fileDate != date){
                clock.totalSeconds = 0.0
            }

            // 3. Wipe session seconds regardless
            clock.sessionSeconds = 0.0

            clockStarted = false

        } else{
            clockStarted = false
        }

        // Create Listeners
        startButton.setOnClickListener{  // start or stop clock
            // 0. Ensure that necessary info exists
            var rate: Double

            try{
                rate = rateEntry.text.toString().toDouble()
                notifyView.text = ""  // If there was previously an error, this wipes it
            } catch(e: java.lang.NumberFormatException){
                notifyView.text = "Please enter a number into \"Your Wage\""
                return@setOnClickListener
            } catch (t: Throwable){
                notifyView.text = "ERROR: ${t.message}"
                return@setOnClickListener
            }

            // 1. Set up clock file if needed
            var startClock: Boolean

            if (!clockStarted){  // If clock hasn't been started
                clock = Clock(Time(LocalDateTime.now()))  // Make a new one with current time
                clock.rate = rate  // Save rate to clock object
                createClockFile(clock)  // Save this clock to device
                startClock = true  // We will need to start the clock
                clockStarted = true
                println("[i] Button pressed while clock inactive. Starting clock")

            } else{
                startClock = false  // We will need to stop the clock
                clockStarted = false
                println("[i] Button pressed while clock active. Stopping clock")
            }

            // 2. Start clock or stop clock
            val endNote: String
            val dbJson: String
            var now: LocalDateTime
            var pay: Double

            if (startClock){  // If we need to start the clock
                clock.active = true  // this needs to be saved to file
                createClockFile(clock)  // Save the newly created clock object
                notifyView.text = "Clock Started!"

            } else{  // Otherwise if we need to stop the clock

                // 1. Prep clock for db store
                now = LocalDateTime.now()  // Get current time
                endNote = clock.startTime.getElapsedTimeAsString(now)  // Get elapsed time str
                clock.createDBEntry(rate)  // This calculates pay and enters it into db
                dbJson = clock.makeJsonString()  // This converts db into json format
                saveFile(dbJson, "payData.json")  // Save json to a file
                println("[i] DB: ${readFile("payData.json")}")

                // 2. Save clock data
                pay = clock.calculatePay(rate, clock.startTime.getElapsedTimeSec(now))
                clock.totalSeconds += clock.sessionSeconds  // Do this because session has ended
                clock.active = false
                createClockFile(clock)
                println("[i] Clock file: ${readFile("clock.pvcf")}")

                // 3. Output final data
                timeWorked.text = endNote
                payView.text = "$$pay"

            }


        }

        resetButton.setOnClickListener {

        }

        displayPayButton.setOnClickListener {

        }

    }
}
